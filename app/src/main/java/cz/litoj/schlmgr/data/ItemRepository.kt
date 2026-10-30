package cz.litoj.schlmgr.data

import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.WordTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.data.db.CounterChain
import cz.litoj.schlmgr.data.db.DaoItemStore
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.FlipAnalysis
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.FlipService
import cz.litoj.schlmgr.data.db.GraphDelete
import cz.litoj.schlmgr.data.db.GraphWalk
import cz.litoj.schlmgr.data.db.ImportService
import cz.litoj.schlmgr.data.db.ImportResult
import cz.litoj.schlmgr.data.db.SQL_IDS_PER_STATEMENT
import cz.litoj.schlmgr.data.db.InsertWordParams
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.LinkOrder
import cz.litoj.schlmgr.data.db.MoveRequest
import cz.litoj.schlmgr.data.db.NewTranslation
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.db.WordEditService
import cz.litoj.schlmgr.data.db.WordMerge

/**
 * Single gateway for all data access.
 * - synchronous facade, and callers hand over item streams.
 * - database calls block on Dispatchers.IO.
 * - the export renders and walks on the caller thread.
 */
object ItemRepository {

	@Volatile
	private var db: ItemDatabase? = null

	/**
	 * Installs the caller-built database, since no Android context exists here.
	 * Must run once on app startup, before the first query.
	 */
	@JvmStatic
	fun init(database: ItemDatabase) {
		db = database
	}

	/**
	 * The unit tests need the real graph logic without the on-disk file.
	 */
	@VisibleForTesting
	internal fun initForTest(database: ItemDatabase) {
		db = database
	}

	/** Drops the test database, so a later test cannot read a closed one. */
	@VisibleForTesting
	internal fun resetForTest() {
		db = null
	}

	/** Callers skip data access when false. */
	@JvmStatic
	val initialized: Boolean get() = db != null

	/**
	 * Must run once on startup, before reading the subject list.
	 * Parentless subjects stay unlinked.
	 */
	@JvmStatic
	fun ensureRoot(): Unit = query { it.itemDao().insertRoot(ROOT_ID) }

	private fun <T> query(block: suspend (ItemDatabase) -> T): T {
		val database = db ?: throw IllegalStateException("ItemRepository not initialized")
		return runBlocking(Dispatchers.IO) { block(database) }
	}

	@JvmStatic
	fun subjects(): List<DbItem> = query { it.itemDao().getChildren(ROOT_ID) }

	@JvmStatic
	fun children(parentId: Int): List<DbItem> = query { it.itemDao().getChildren(parentId) }

	/**
	 * Parent set of every direct child, the browse reference facts.
	 * - a browse load shows the children only, but needs every parent link.
	 * - the strip then knows which rows are references and whether they still live elsewhere.
	 * - one batch read replaces one lookup per row.
	 */
	fun childLinksOf(parentId: Int): List<DbLink> = query { it.itemDao().childLinksOf(parentId) }

	/**
	 * One batch read for the default-flipped rows of a browse load.
	 */
	fun childTranslations(parentId: Int): Map<Int, List<DbItem>> = query {
		it.wordDao().childTranslations(parentId).groupBy(WordTranslation::wordId) { it.item }
	}

	@JvmStatic
	fun get(id: Int): DbItem? = query { it.itemDao().get(id) }

	@JvmStatic
	@VisibleForTesting
	fun getByName(name: String, kind: ItemKind): List<DbItem> =
		query { it.itemDao().getByName(name, kind) }

	@JvmStatic
	fun translations(wordId: Int): List<DbItem> = query { it.wordDao().getTranslations(wordId) }

	@JvmStatic
	fun parentIds(childId: Int): List<Int> = query { it.itemDao().parentsOf(childId) }

	/** The rows that link to [childId] in link order, the words of a translation. */
	@JvmStatic
	fun parentRows(childId: Int): List<DbItem> = query { it.itemDao().parentRowsOf(childId) }

	/**
	 * Container chain down to parent, subject first, root excluded.
	 * Multi-parent rows take first parent found.
	 */
	@JvmStatic
	fun pathTo(parentId: Int): List<DbItem> = query {
		val dao = it.itemDao()
		val chain = ArrayList<DbItem>()
		var cur = dao.get(parentId)
		while (cur != null && cur.id != ROOT_ID) {
			chain.add(cur)
			cur = dao.anyParentOf(cur.id)
		}
		chain.reverse()
		chain
	}

	@JvmStatic
	fun subgraph(rootId: Int): List<DbItem> = query { it.itemDao().subgraph(rootId) }

	/**
	 * Distinct word and chapter counts of the union, the roots included.
	 * Shared rows count once per chunk, which is cosmetic only.
	 */
	@JvmStatic
	fun subgraphStats(roots: Collection<Int>): Pair<Int, Int> = query {
		var words = 0
		var chapters = 0
		for (chunk in roots.chunked(SQL_IDS_PER_STATEMENT))
			for (count in it.itemDao().subgraphTypeCounts(chunk))
				if (count.kind == ItemKind.WORD) words += count.amount
				else chapters += count.amount
		Pair(words, chapters)
	}

	fun subgraphLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphLinks(rootId) }

	/**
	 * Every link into the subgraph, the whole parent set with the outside included.
	 * - the search preloads it, so hit rows need no per-row lookup.
	 * - an outside parent matters because a reference link can sit outside the subgraph.
	 * - the search row must still know where to jump to.
	 */
	fun subgraphChildLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphChildLinks(rootId) }

	@JvmStatic
	fun createSubject(name: String, description: String?): DbItem {
		// a blank name cannot be stored, since no export format round-trips it
		require(name.isNotBlank()) { "A subject needs a name" }
		return query {
			it.withTransaction {
				val dao = it.itemDao()
				val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.CHAPTER)).toInt()
				LinkOrder.linkAt(dao, ROOT_ID, id, null)
				dao.get(id)!!
			}
		}
	}

	@JvmStatic
	@JvmOverloads
	fun createChapter(parentId: Int, name: String, description: String?, position: Int? = null): DbItem {
		// a blank name cannot be stored, since no export format round-trips it
		require(name.isNotBlank()) { "A chapter needs a name" }
		return query {
			it.withTransaction {
				val dao = it.itemDao()
				val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.CHAPTER)).toInt()
				LinkOrder.linkAt(dao, parentId, id, position)
				dao.get(id)!!
			}
		}
	}

	@JvmStatic
	@JvmOverloads
	fun createNote(parentId: Int, name: String, description: String?, position: Int? = null): DbItem {
		// a blank name cannot be stored, since no export format round-trips it
		require(name.isNotBlank()) { "A note needs a name" }
		return query {
			it.withTransaction {
				val dao = it.itemDao()
				val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.NOTE)).toInt()
				LinkOrder.linkAt(dao, parentId, id, position)
				dao.get(id)!!
			}
		}
	}

	/**
	 * A word with translations as pairs, and same-named words merge instead of doubling.
	 * - every word carries a translation, since a row without one is a note.
	 */
	@JvmStatic
	fun createWord(
		parentId: Int,
		name: String,
		description: String?,
		translations: List<Pair<String, String?>>,
	): DbItem {
		// a blank name cannot be stored, since no export format round-trips it
		require(name.isNotBlank()) { "A word needs a name" }
		// a blank name carries no translation once trimmed
		require(translations.any { it.first.trim().isNotEmpty() }) {
			"A word needs at least one translation — make a note instead"
		}
		return query {
			it.withTransaction {
				val dao = it.itemDao()
				val word = WordMerge.insertWord(DaoItemStore(dao), InsertWordParams(parentId, name, description,
					translations.map { NewTranslation(it.first, it.second) }))
				// a merge onto an existing word brings its counters into the
				// derivation of the parent, and no other create path changes them
				recomputeCounterChain(dao, parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
				word
			}
		}
	}

	//
	// saves converted item streams and reads them back, while parsing and rendering
	// live with the codecs.

	/**
	 * Saves an item stream in one transaction, order preserved.
	 * - null parents take the anchor.
	 * - words merge by name in scope, containers by name and type.
	 * - unresolvable references are skipped.
	 */
	@JvmStatic
	fun import(importer: DataImporter, anchorId: Int = ROOT_ID): ImportResult = query {
		it.withTransaction { ImportService.run(it.itemDao(), importer, anchorId).result }
	}

	/**
	 * Reads the container content as a stream and renders it.
	 * - the whole walked stream hands over through [DataExporter.onStream] before the render.
	 * - its size is the target of the progress bar, with each item one written object.
	 */
	@JvmStatic
	fun export(exporter: DataExporter, containers: List<DbItem>): String {
		val stream = walkExport(containers)
		exporter.onStream(stream)
		return exporter.render(stream)
	}

	/**
	 * Pre-order stream of the container content, each container first, then its rows below.
	 * - every subtree preloads, and shared links count once.
	 * - a reference child writes as its plain-home path under [containers].
	 * - it appears at its home only.
	 * - a home outside the export writes its content in place, or the save would lose it.
	 */
	private fun walkExport(containers: List<DbItem>): List<StreamItem> = query { database ->
		// one transaction spans the walk, so a concurrent import cannot drop
		// a row between the reads
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

	/**
	 * Renames an item.
	 * - a blank name refuses, since no export format round-trips it.
	 * - a word colliding with another word of the same subject merges into it.
	 * - links and translations move to the surviving row.
	 * - the renamed row is deleted.
	 */
	@JvmStatic
	fun rename(id: Int, name: String): DbItem = query {
		it.withTransaction { WordEditService.rename(it.itemDao(), id, name) }
	}

	@JvmStatic
	fun setDescription(id: Int, description: String?): Unit = query {
		val item = it.itemDao().get(id) ?: return@query
		// null clears, since the column is non-null and an absent value is the empty text
		it.itemDao().updateById(id, item.name, description ?: "")
	}

	/**
	 * Whether the edit forks, when a shared word is renamed and loses a translation.
	 * - the word is shared when a chapter holds it through a plain link.
	 * - the rename licenses the fork, which avoids a duplicate row.
	 * - a word that no longer exists throws, like the edit itself.
	 * - the UI asks first to show the reference popup.
	 */
	@JvmStatic
	fun editForks(edit: WordEdit): Boolean = query {
		it.withTransaction { WordEditService.forks(it.itemDao(), edit) }
	}

	/** Items showing the word through a reference link, the fork popup list. */
	@JvmStatic
	fun referencesOf(wordId: Int): List<DbItem> = query { it.itemDao().referenceParentsOf(wordId) }

	/**
	 * Applies a committed word edit.
	 * - it forks when the word is shared, renamed and lost a translation.
	 * - otherwise it edits in place, and a rename-join keeps the survivor.
	 * - a blank name refuses, since no export format round-trips it.
	 * - an edit that leaves the word without a translation refuses.
	 */
	@JvmStatic
	fun editWord(edit: WordEdit): DbItem = query {
		it.withTransaction { WordEditService.edit(it.itemDao(), edit) }
	}

	/**
	 * Swaps words with translations and expands chapters to words in one pass.
	 * - no conflict loop of [startFlip], [advanceFlip] and [commitFlip] runs here.
	 * - name collisions merge.
	 * - shared translations are replaced, not orphaned.
	 * - references follow the flip.
	 */
	@JvmStatic
	fun flip(ids: List<Int>): Unit = query {
		it.withTransaction { FlipService.flipIds(it.itemDao(), ids) }
	}

	/**
	 * Starts a flip run with the plan and the first round of relations that
	 * span the selection and the outside. See FlipConflict.
	 */
	@JvmStatic
	fun startFlip(actingParents: Collection<Int>, ids: List<Int>): FlipAnalysis = query {
		FlipService.start(it.itemDao(), actingParents, ids)
	}

	/**
	 * Applies one popup round and re-analyzes, the loop step between
	 * [startFlip] and [commitFlip].
	 */
	@JvmStatic
	fun advanceFlip(analysis: FlipAnalysis, resolutions: List<FlipResolution>): FlipAnalysis = query {
		FlipService.advance(it.itemDao(), analysis, resolutions)
	}

	/** Runs the resolved plan, the custom versions first, then the flip. */
	@JvmStatic
	fun commitFlip(analysis: FlipAnalysis): Unit = query {
		// a conflicting plan has open popup rounds, so committing it would
		// silently drop the answers of the user
		require(analysis.conflicts.isEmpty()) { "The flip plan still holds conflicts" }
		it.withTransaction { FlipService.execute(it.itemDao(), analysis.plan) }
	}

	/**
	 * Moves an item between containers of one subject, never copied.
	 * - a null position appends at the end.
	 * - [MoveResult.REFUSED_CYCLE]: the move would close a cycle.
	 * - [MoveResult.REFUSED_OTHER_SUBJECT]: the target sits in another subject.
	 * - use the subject merge for that case.
	 * - [MoveResult.ALREADY_PRESENT]: the target already links the row.
	 * - nothing changes on refusal.
	 */
	@JvmStatic
	@JvmOverloads
	fun move(
		childId: Int, oldParentId: Int, newParentId: Int, position: Int? = null,
	): MoveResult = query {
		it.withTransaction {
			val dao = it.itemDao()
			// items stay in one subject, so moving into another subject refuses
			if (dao.subjectRootOf(childId, ROOT_ID) != dao.subjectRootOf(newParentId, ROOT_ID))
				return@withTransaction MoveResult.REFUSED_OTHER_SUBJECT
			if (dao.linkPosition(newParentId, childId) != null)
				return@withTransaction MoveResult.ALREADY_PRESENT
			if (moveInternal(dao, MoveRequest(childId, oldParentId, newParentId, position)))
				MoveResult.MOVED else MoveResult.REFUSED_CYCLE
		}
	}

	enum class MoveResult { MOVED, REFUSED_CYCLE, REFUSED_OTHER_SUBJECT, ALREADY_PRESENT }

	/**
	 * Moves a whole subject into a container of another subject.
	 * - the subject row becomes a chapter, and same-named rows merge below.
	 * - reference links re-create, and outside-shared rows survive.
	 * - returns false for a target inside the moved subject or the root.
	 */
	@JvmStatic
	fun mergeSubject(sourceId: Int, targetParentId: Int): Boolean = query {
		it.withTransaction {
			val dao = it.itemDao()
			if (targetParentId == ROOT_ID) return@withTransaction false
			val source = dao.get(sourceId) ?: return@withTransaction false
			if (closesCycle(dao, sourceId, targetParentId)) return@withTransaction false
			// plain-link containment tree in link order, and reference links ride separately
			val rows = dao.subgraph(sourceId)
			val byId = HashMap<Int, DbItem>(rows.size * 2)
			for (row in rows) byId[row.id] = row
			val children = HashMap<Int, MutableList<DbItem>>()
			for (link in dao.subgraphLinks(sourceId)
				.sortedWith(compareBy({ it.position }, { it.childId }))) {
				val child = byId[link.childId] ?: continue
				if (link.isReference) continue
				children.getOrPut(link.parentId) { ArrayList() }.add(child)
			}
			// the stream root anchors at the target.
			val items = ArrayList<StreamItem>()
			val rootItem = StreamItem(source, null)
			items.add(rootItem)
			GraphWalk.collectInto(items, rootItem, { walked ->
				val kids = children[walked.id] ?: emptyList()
				// words list translations only.
				val visible = if (walked.type == ItemKind.WORD)
					kids.filter { it.type == ItemKind.TRANSLATION } else kids
				visible.map { StreamChild(it) }
			})
			val outcome = ImportService.run(dao, StreamImporter(items), targetParentId)
			// reference links follow their rows, so in-tree endpoints remap
			// and outside ones keep the row
			val mergedOf = HashMap<Int, Int>()
			for (entry in items) mergedOf[entry.item.id] = outcome.savedRows[entry] ?: continue
			for (link in dao.subgraphChildLinks(sourceId).filter { it.isReference }) {
				val parent = mergedOf[link.parentId] ?: link.parentId
				val child = mergedOf[link.childId] ?: link.childId
				// an outside parent survives the delete, since its link would
				// keep the dying row alive
				if (parent == link.parentId && child != link.childId)
					dao.deleteLink(link.parentId, link.childId)
				if (dao.linkPosition(parent, child) == null)
					dao.insertLink(DbLink(parent, child, dao.nextPosition(parent), isReference = true))
			}
			// moved content lives in the target, and the re-created reference links
			// add no counters, so only the plain chain recomputes
			recomputeCounterChain(dao, targetParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			// moved content lives in the target, so the source goes unless still referenced
			dao.deleteLink(ROOT_ID, sourceId)
			if (dao.parentCount(sourceId) == 0) GraphDelete.delete(dao, sourceId)
			true
		}
	}

	/** Ready item stream as an import source, the subject merge tree. */
	private class StreamImporter(override val items: List<StreamItem>) : DataImporter {
		override val references: List<StreamReference> = emptyList()
	}

	private suspend fun moveInternal(dao: ItemDao, req: MoveRequest): Boolean {
		if (closesCycle(dao, req.childId, req.newParentId)) return false
		// the word sums of the subtree, one chain loses them and the other gains them
		val stats = dao.subgraphWordCounters(req.childId)
		val plainLeave = dao.linkIsReference(req.oldParentId, req.childId) != true
		dao.deleteLink(req.oldParentId, req.childId)
		// read before the new link lands, or it would count as an outside entry
		val shared = (stats.passed != 0 || stats.failed != 0) &&
			dao.plainLinksIntoSubtree(req.childId) > 0
		LinkOrder.linkAt(dao, req.newParentId, req.childId, req.position)
		if (stats.passed == 0 && stats.failed == 0) return true
		if (shared) {
			// the subtree is shared, so only the exact sums stay right
			if (plainLeave)
				recomputeCounterChain(dao, req.oldParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			recomputeCounterChain(dao, req.newParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		} else {
			if (plainLeave)
				CounterChain.shiftCounterChain(dao, req.oldParentId, -stats.passed, -stats.failed, dao::parentsPlainFirst)
			CounterChain.shiftCounterChain(dao, req.newParentId, stats.passed, stats.failed, dao::parentsPlainFirst)
		}
		return true
	}

	/**
	 * True when linking the child under the target would close a cycle.
	 * The target then sits inside the child content, the child itself included.
	 */
	private suspend fun closesCycle(dao: ItemDao, childId: Int, targetId: Int): Boolean =
		dao.subgraph(childId).any { it.id == targetId }

	/**
	 * Places an existing item into another container through a reference link.
	 * - any item, a chapter included, can be referenced.
	 * - the whole content shows through the link alone, with no per-item copies.
	 * - no counter changes, because a reference never counts toward the rate of the parent.
	 * - returns true without change when the link already exists.
	 * - returns false when the link would close a cycle.
	 * - a referenced row keeps its plain home, because subject walks take plain links first.
	 * - a cross-subject reference never changes the subject of a home-holding row.
	 */
	@JvmStatic
	fun reference(childId: Int, parentId: Int): Boolean = query {
		it.withTransaction {
			val dao = it.itemDao()
			if (dao.linkPosition(parentId, childId) != null) return@withTransaction true
			if (closesCycle(dao, childId, parentId)) return@withTransaction false
			dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId), isReference = true))
			true
		}
	}

	@JvmStatic
	fun linkIsReference(parentId: Int, childId: Int): Boolean =
		query { it.itemDao().linkIsReference(parentId, childId) == true }

	/**
	 * Removes an item from one container.
	 * - a last parent gone means the reachable graph dies, while shared items survive.
	 * - each removed row reports to progress.
	 */
	@JvmStatic
	@JvmOverloads
	fun remove(childId: Int, parentId: Int?, progress: Progress? = null): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			// a word with no translations goes with the last translation,
			// since only container links sever
			if (parentId != null && isLastTranslation(dao, childId, parentId)) {
				val wordParents = dao.parentsPlainFirst(parentId)
				// read before the severing, since the plain parents are the chains to update
				val plainParents = dao.plainParentsOf(parentId)
				for (parent in wordParents) dao.deleteLink(parent, parentId)
				updateChainsAfterRemoval(dao, parentId, plainParents)
				GraphDelete.delete(dao, parentId) { progress?.advance(1) }
				return@withTransaction
			}
			// a severed reference never counted, so no chain moves
			val refLink = parentId != null && dao.linkIsReference(parentId, childId) == true
			if (parentId != null) dao.deleteLink(parentId, childId)
			if (parentId != null && !refLink) updateChainsAfterRemoval(dao, childId, listOf(parentId))
			if (dao.parentCount(childId) == 0) GraphDelete.delete(dao, childId) { progress?.advance(1) }
		}
	}

	/**
	 * Updates the chains above [plainParents] for a subtree that just left them.
	 * - the stats travel as a delta.
	 * - a shared subtree gets the exact recompute, since plain links enter it from outside.
	 * - parts of a shared subtree stay reachable, so their sums must not simply move.
	 * - nothing happens for an untested subtree.
	 */
	private suspend fun updateChainsAfterRemoval(
		dao: ItemDao,
		removedId: Int,
		plainParents: List<Int>,
	) {
		if (plainParents.isEmpty()) return
		val stats = dao.subgraphWordCounters(removedId)
		if (stats.passed == 0 && stats.failed == 0) return
		if (dao.plainLinksIntoSubtree(removedId) > 0) {
			for (parent in plainParents)
				recomputeCounterChain(dao, parent, dao::subgraphWordCounters, dao::parentsPlainFirst)
			return
		}
		for (parent in plainParents)
			CounterChain.shiftCounterChain(dao, parent, -stats.passed, -stats.failed, dao::parentsPlainFirst)
	}

	private suspend fun isLastTranslation(dao: ItemDao, childId: Int, parentId: Int): Boolean =
		dao.get(childId)?.type == ItemKind.TRANSLATION &&
			dao.get(parentId)?.type == ItemKind.WORD &&
			dao.getChildren(parentId).none { it.type == ItemKind.TRANSLATION && it.id != childId }

	/**
	 * Puts the child at a 0-based index, and the slots between shift by one.
	 * Raw positions carry gaps, so the index maps onto the occupying slot.
	 */
	@JvmStatic
	fun reorder(parentId: Int, childId: Int, newIndex: Int): Unit = query {
		it.withTransaction { LinkOrder.reorder(it.itemDao(), parentId, childId, newIndex) }
	}

	/**
	 * Persists the manual child order.
	 * See [LinkOrder.setOrder] for the slot rules.
	 */
	@JvmStatic
	fun setOrder(parentId: Int, orderedChildIds: List<Int>): Unit = query {
		it.withTransaction { LinkOrder.setOrder(it.itemDao(), parentId, orderedChildIds) }
	}

	/**
	 * Records one passed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordPass(ids: Collection<Int>): Unit = query {
		if (ids.isEmpty()) return@query
		it.withTransaction { for (chunk in ids.chunked(SQL_IDS_PER_STATEMENT)) it.itemDao().addPassed(chunk) }
	}

	/**
	 * Records one failed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordFail(ids: Collection<Int>): Unit = query {
		if (ids.isEmpty()) return@query
		it.withTransaction { for (chunk in ids.chunked(SQL_IDS_PER_STATEMENT)) it.itemDao().addFailed(chunk) }
	}

	/**
	 * Records one test answer on every given row: the shown translations of the tested word.
	 * Containers derive their counters through [bumpWordCounters].
	 */
	@JvmStatic
	fun bumpCounters(ids: Collection<Int>, passed: Boolean): Unit =
		if (passed) recordPass(ids) else recordFail(ids)

	/**
	 * Records one test answer on the word and on every container whose sums hold it.
	 * - these are the plain ancestors of the word.
	 * - reference links never count.
	 * - a shared container meets the word through several parents but holds it once.
	 */
	@JvmStatic
	fun bumpWordCounters(wordId: Int, passed: Boolean): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			val passedDelta = if (passed) 1 else 0
			val failedDelta = if (passed) 0 else 1
			dao.addCounters(wordId, passedDelta, failedDelta)
			val seen = HashSet<Int>()
			seen.add(wordId)
			for (parent in dao.plainParentsOf(wordId))
				CounterChain.walkAbove(parent, dao::plainParentsOf) { id ->
					if (seen.add(id)) dao.addCounters(id, passedDelta, failedDelta)
				}
		}
	}

	/**
	 * Recomputes the stored success/fail of a container from the words in its subgraph.
	 * The fresh counters are returned as well.
	 */
	@JvmStatic
	fun recomputeCounters(containerId: Int): Counters = query {
		CounterChain.recomputeCounters(it.itemDao(), containerId)
	}

	/**
	 * Recomputes the counters of [start] and of every container above it.
	 * - it walks all parents, each container once, and shared containers carry sums.
	 * - never the root, since its sum spans the whole database and shows nowhere.
	 * - callers supply the sums and the parents.
	 */
	internal suspend fun recomputeCounterChain(
		dao: ItemDao,
		start: Int,
		countersOf: suspend (Int) -> Counters,
		parentsOf: suspend (Int) -> List<Int>,
	) = CounterChain.recomputeCounterChain(dao, start, countersOf, parentsOf)

	/** Direct row insert for tests only, with no merge and explicit counters. */
	@VisibleForTesting
	internal fun legacyInsert(
		name: String, description: String?, kind: ItemKind, passed: Int, failed: Int,
	): Int = query {
		it.itemDao().insert(
			DbItem(
				name = name, description = description ?: "", type = kind,
				passedTests = passed, failedTests = failed,
			)
		).toInt()
	}

	@VisibleForTesting
	internal fun legacyLinkAtEnd(parentId: Int, childId: Int): Unit = query {
		val dao = it.itemDao()
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId)))
	}
}
