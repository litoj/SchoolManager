package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * The read model of one flip run, seeded by the flip action ids.
 * - one read keeps every parameter list short, no list grows with the data.
 * - it holds the seed subtrees, their ancestors, every name match and every link into them.
 * - analysis, ordering and pair detection work from the derived maps.
 * - the flip writes change data as it runs, so name lookups re-read each candidate fresh.
 * - deletions drop out of the fresh re-read, and inserts join through [inserted].
 * - only the subject chain follows the preloaded links.
 */
internal class FlipScope private constructor(
	private val dao: ItemDao,
	rows: List<DbItem>,
	links: List<DbLink>,
) {

	private val byId = HashMap<Int, DbItem>()

	/** Merge-candidate ids per name, words and translations alike. */
	private val candidatesByName = HashMap<String, MutableList<Int>>()

	/** The links into a row in link order, the whole parent set of the row. */
	private val linksInto = HashMap<Int, MutableList<DbLink>>()

	/** The links out of a row in link order, matching [ItemDao.getChildren]. */
	private val linksOf = HashMap<Int, MutableList<DbLink>>()

	init {
		for (row in rows) {
			byId[row.id] = row
			if (row.type == ItemKind.WORD || row.type == ItemKind.TRANSLATION)
				candidatesByName.getOrPut(row.name) { ArrayList() }.add(row.id)
		}
		for (link in links) {
			// both ends of every fetched link are preloaded rows
			if (link.parentId !in byId || link.childId !in byId) continue
			linksInto.getOrPut(link.childId) { ArrayList() }.add(link)
			linksOf.getOrPut(link.parentId) { ArrayList() }.add(link)
		}
		for (list in linksInto.values) list.sortBy { it.position }
		for (list in linksOf.values) list.sortWith(compareBy({ it.position }, { it.childId }))
	}

	/** The preloaded row of [id], null outside the scope. The flip reads it live per word. */
	fun row(id: Int): DbItem? = byId[id]

	/** Child rows of [parentId] in the same order as [ItemDao.getChildren]. */
	fun children(parentId: Int): List<DbItem> =
		linksOf[parentId]?.mapNotNull { byId[it.childId] } ?: emptyList()

	fun subtree(rootId: Int): List<DbItem> {
		val out = ArrayList<DbItem>()
		val seen = HashSet<Int>()
		val queue = ArrayDeque<Int>()
		if (row(rootId) == null) return out
		queue.add(rootId)
		while (queue.isNotEmpty()) {
			val id = queue.removeFirst()
			if (!seen.add(id)) continue
			row(id)?.let(out::add)
			for (child in children(id)) queue.add(child.id)
		}
		return out
	}

	/** The parents of [id] in link order, plain links and reference links alike. */
	fun parents(id: Int): List<Int> = linksInto[id]?.map { it.parentId } ?: emptyList()

	/** The rows linking to [id] in link order, the holders of a translation. */
	fun parentRows(id: Int): List<DbItem> =
		linksInto[id]?.mapNotNull { byId[it.parentId] } ?: emptyList()

	/** The names of the translations of [wordId], the exclusion set of the flip. */
	fun translationNames(wordId: Int): Set<String> =
		children(wordId).asSequence().filter { it.type == ItemKind.TRANSLATION }.map { it.name }.toSet()

	/**
	 * The merge lookup of the flip, a [kind] row named [name] under [subjectRoot].
	 * - it answers as [ItemDao.findWordInSubject] would, over the preload.
	 * - each candidate re-reads fresh, so writes of the run show in it.
	 */
	suspend fun wordInSubject(subjectRoot: Int, name: String, kind: ItemKind): DbItem? {
		val candidates = candidatesByName[name] ?: return null
		for (id in candidates) {
			val row = dao.get(id) ?: continue
			if (row.type != kind) continue
			if (inSubject(row.id, subjectRoot)) return row
		}
		return null
	}

	fun inserted(row: DbItem) {
		candidatesByName.getOrPut(row.name) { ArrayList() }.add(row.id)
	}

	/** The subject root of the row, the ancestor below the invisible root. */
	suspend fun subjectRootOf(id: Int): Int? {
		var cursor = id
		// a link cycle cannot reach the root, the walk stops on the first repeat
		val seen = hashSetOf(id)
		while (true) {
			val parent = parentHop(cursor) ?: return null
			if (parent == ROOT_ID) return cursor
			if (!seen.add(parent)) return null
			cursor = parent
		}
	}

	/**
	 * Whether the chain of rowId passes subjectRoot, one parent per hop.
	 * - a home-holding row stays in its subject, a row without a plain home follows its reference.
	 * - a link cycle cannot reach the root, the walk stops on the first repeat.
	 */
	private suspend fun inSubject(rowId: Int, subjectRoot: Int): Boolean {
		var cursor = rowId
		val seen = hashSetOf(rowId)
		while (true) {
			val parent = parentHop(cursor) ?: return false
			if (parent == subjectRoot) return true
			if (parent == ROOT_ID || !seen.add(parent)) return false
			cursor = parent
		}
	}

	/** One parent of [id], plain links first, from the preload with the database as fallback. */
	private suspend fun parentHop(id: Int): Int? {
		val link = linksInto[id]?.minByOrNull { it.isReference }
		if (link != null) return link.parentId
		return dao.anyParentOf(id)?.id
	}

	companion object {

		/**
		 * Reads the scope of the seeds, the flip action ids.
		 * - the seed list splits at the SQLite host parameter cap.
		 * - a giant selection then only costs more round trips, it never breaks a query.
		 */
		suspend fun of(dao: ItemDao, seeds: Collection<Int>): FlipScope {
			if (seeds.isEmpty()) return FlipScope(dao, emptyList(), emptyList())
			if (seeds.size <= ItemRepository.SQL_IDS_PER_STATEMENT)
				return FlipScope(dao, dao.flipScopeRows(seeds), dao.flipScopeLinks(seeds))
			val rows = ArrayList<DbItem>()
			val links = ArrayList<DbLink>()
			for (chunk in seeds.chunked(ItemRepository.SQL_IDS_PER_STATEMENT)) {
				rows.addAll(dao.flipScopeRows(chunk))
				links.addAll(dao.flipScopeLinks(chunk))
			}
			return FlipScope(dao, rows, links)
		}
	}
}
