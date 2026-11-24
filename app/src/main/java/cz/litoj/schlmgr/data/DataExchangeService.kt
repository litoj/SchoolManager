package cz.litoj.schlmgr.data

import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.GraphWalk
import cz.litoj.schlmgr.data.db.ImportResult
import cz.litoj.schlmgr.data.db.ImportService
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.SQL_IDS_PER_STATEMENT

/** Item-stream imports and exports, with caller-owned scope. */
class DataExchangeService internal constructor(private val database: ItemDatabase) {

	/**
	 * Saves an item stream in one transaction, order preserved.
	 * - null parents take the anchor.
	 * - words merge by name in scope, containers by name and type.
	 * - unresolvable references are skipped.
	 */
	suspend fun import(importer: DataImporter, anchorId: Int = ROOT_ID): ImportResult =
		database.withTransaction { ImportService.run(database.itemDao(), importer, anchorId).result }

	/**
	 * Reads the container content as a stream and renders it.
	 * - the whole walked stream hands over through [DataExporter.onStream] before the render.
	 * - its size is the target of the progress bar, with each item one written object.
	 */
	suspend fun export(exporter: DataExporter, containers: List<DbItem>): String {
		val stream = walkExport(containers)
		exporter.onStream(stream)
		return exporter.render(stream)
	}

	/**
	 * Distinct word and chapter counts of the union, the roots included.
	 * Shared rows count once per chunk, which is cosmetic only.
	 */
	suspend fun subgraphStats(roots: Collection<Int>): Pair<Int, Int> {
		var words = 0
		var chapters = 0
		for (chunk in roots.chunked(SQL_IDS_PER_STATEMENT))
			for (count in database.itemDao().subgraphTypeCounts(chunk))
				if (count.kind == ItemKind.WORD) words += count.amount
				else chapters += count.amount
		return Pair(words, chapters)
	}

	/**
	 * Pre-order stream of the container content, each container first, then its rows below.
	 * - every subtree preloads, and shared links count once.
	 * - a reference child writes as its plain-home path under [containers].
	 * - it appears at its home only.
	 * - a home outside the export writes its content in place, or the save would lose it.
	 */
	private suspend fun walkExport(containers: List<DbItem>): List<StreamItem> =
		database.withTransaction {
			val dao = database.itemDao()
			val out = ArrayList<StreamItem>()
			val itemsById = HashMap<Int, DbItem>()
			for (container in containers)
				for (row in dao.subgraph(container.id)) itemsById[row.id] = row
			val links = LinkedHashSet<DbLink>()
			for (container in containers)
				links.addAll(dao.subgraphLinks(container.id))
			// sort links by position, matching the live per-node order
			val sorted = links.sortedWith(compareBy({ it.position }, { it.childId }))
			val rootIds = HashSet<Int>()
			for (container in containers) rootIds.add(container.id)
			// plain parents by child, since every reference path climbs them to a root,
			// so they gather before any path computes
			val plainParents = HashMap<Int, MutableList<Int>>()
			for (link in sorted) if (!link.isReference)
				plainParents.getOrPut(link.childId) { ArrayList() }.add(link.parentId)
			val children = HashMap<Int, MutableList<StreamChild>>()
			for (link in sorted) {
				val child = itemsById[link.childId] ?: continue
				val refPath =
					if (link.isReference) refPathOf(child, itemsById, plainParents, rootIds) else null
				children.getOrPut(link.parentId) { ArrayList() }.add(StreamChild(child, refPath))
			}
			for (container in containers) {
				val root = StreamItem(container, null)
				out.add(root)
				GraphWalk.collectInto(out, root,
					{ walked ->
						val kids = children[walked.id] ?: emptyList()
						// words list translations only.
						if (walked.type == ItemKind.WORD)
							kids.filter { it.row.type == ItemKind.TRANSLATION }
						else kids
					},
				)
			}
			out
		}

	/**
	 * The plain-home path of a reference target: names from an exported root down.
	 * - the name of the target comes last.
	 * - the root row alone names a target that is a root.
	 * - null when no plain chain reaches a root of this export.
	 */
	private fun refPathOf(
		target: DbItem,
		itemsById: Map<Int, DbItem>,
		plainParents: Map<Int, List<Int>>,
		rootIds: Set<Int>,
	): List<String>? {
		if (target.id in rootIds) return listOf(target.name)
		val names = ArrayDeque<String>()
		names.addFirst(target.name)
		var cursor = target.id
		while (true) {
			val parent = plainParents[cursor]?.firstOrNull { it in itemsById } ?: return null
			names.addFirst(itemsById[parent]!!.name)
			if (parent in rootIds) return names.toList()
			cursor = parent
		}
	}
}
