package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.Counters

/**
 * One import run working set.
 * - merge base preloads once, rows and links flush in bulk at end.
 * - store behind repository merge rule, not copy of it.
 */
internal class ImportSession private constructor(private val dao: ItemDao) : ItemStore {

	internal companion object {
		/**
		 * Loads the merge base of one import.
		 * - container import — the anchor subject's whole subgraph, once.
		 * - root import — the root's children, plus a joined subject's scope on demand.
		 * - fresh subjects load no base at all.
		 */
		suspend fun load(dao: ItemDao, anchorId: Int): ImportSession {
			val session = ImportSession(dao)
			if (anchorId == ROOT_ID) {
				for (row in dao.getChildren(ROOT_ID)) session.indexLoadedRow(row)
				for (link in dao.linksOf(ROOT_ID)) session.indexLoadedLink(link)
				session.sortChildren()
			} else {
				session.loadScope(dao.subjectRootOf(anchorId, ROOT_ID) ?: anchorId)
			}
			return session
		}
	}

	/** Every row of the loaded scopes, by id — new rows as they are created. */
	private val rows = HashMap<Int, DbItem>()

	/** Name -> kind -> candidate row ids. The merge lookups scan these. */
	private val byName = HashMap<String, HashMap<ItemKind, ArrayList<Int>>>()

	/** Child id -> its incoming links. */
	private val parents = HashMap<Int, ArrayList<DbLink>>()

	/** Parent id -> its outgoing links, in link order. */
	private val children = HashMap<Int, ArrayList<DbLink>>()

	/** Parent id -> the child ids linked under it (the [children] fast path). */
	private val childIds = HashMap<Int, HashSet<Int>>()

	/** Parent id -> one past the current end of its child list. */
	private val nextPositions = HashMap<Int, Int>()

	/** Placeholder ids of the rows to insert, in creation order. */
	private val pending = ArrayList<Int>()

	/** Links to insert, with placeholder ids where they touch new rows. */
	private val pendingLinks = ArrayList<DbLink>()

	/** Existing rows whose description a merge changed — the flush writes them. */
	private val pendingDescriptions = HashMap<Int, DbItem>()

	/** Rows whose counters a merge adopted — the counter phase refreshes their containers. */
	internal val adopted = HashSet<Int>()

	/** Next placeholder id — negative, counting down. Real rows are non-negative. */
	private var nextPlaceholder = -1

	// ------------------------------------------------------------ lookups

	/** The row behind [id], as the import sees it now (merges update it). */
	override suspend fun row(id: Int): DbItem? = rows[id]

	/** Whether [id] is a row this session knows — the caller's SQL fallback gate. */
	fun knows(id: Int): Boolean = rows.containsKey(id)

	/**
	 * The subject containing an item — the nearest ancestor that is a child of
	 * the root (the item itself included), a breadth-first pick.
	 */
	override suspend fun subjectRootOf(itemId: Int): Int? {
		if (isRootChild(itemId)) return itemId
		val visited = HashSet<Int>()
		visited.add(itemId)
		val queue = ArrayDeque<Int>()
		queue.add(itemId)
		while (queue.isNotEmpty()) {
			val links = parents[queue.removeFirst()] ?: continue
			for (link in links) {
				val parent = link.parentId
				if (parent == ROOT_ID) continue
				if (!visited.add(parent)) continue
				if (isRootChild(parent)) return parent
				queue.add(parent)
			}
		}
		return null
	}

	/**
	 * The first row of a kind and name that a scope reaches — the in-memory
	 * form of the DAO's subject-scope lookup. Reachable = the scope sits
	 * among the row's ancestors, reference links included.
	 */
	override suspend fun findInSubject(scope: Int, name: String, kind: ItemKind): DbItem? {
		val candidates = byName[name]?.get(kind) ?: return null
		for (id in candidates) if (reaches(scope, id)) return rows[id]
		return null
	}

	/** The children of [parentId] in link order, as the live query returned them. */
	fun children(parentId: Int): List<DbItem> =
		children[parentId]?.mapNotNull { rows[it.childId] } ?: emptyList()

	/**
	 * The plain children of [parentId] in link order — a reference holds no
	 * name in its parent, the shown content is not the parent's own.
	 */
	private fun plainChildren(parentId: Int): List<DbItem> =
		children[parentId]?.filter { !it.isReference }?.mapNotNull { rows[it.childId] } ?: emptyList()

	/**
	 * Whether [rowId] lies in [containerId]'s content — the session's links
	 * included, so an import's own pending rows answer before the flush.
	 */
	fun contains(containerId: Int, rowId: Int): Boolean {
		val seen = HashSet<Int>()
		seen.add(containerId)
		val queue = ArrayDeque<Int>()
		queue.add(containerId)
		while (queue.isNotEmpty()) {
			for (child in children(queue.removeFirst())) {
				if (child.id == rowId) return true
				if (seen.add(child.id)) queue.add(child.id)
			}
		}
		return false
	}

	override suspend fun linked(parentId: Int, childId: Int): Boolean =
		childIds[parentId]?.contains(childId) == true

	fun nextPosition(parentId: Int): Int = nextPositions[parentId] ?: 0

	/**
	 * [id]'s parents, plain links first — the counter refresh walks the first
	 * one up. A reference parent leads only with no plain parent left.
	 */
	fun parentsOf(id: Int): List<Int> {
		val links = parents[id] ?: return emptyList()
		return (links.filter { !it.isReference } + links.filter { it.isReference })
			.map { it.parentId }
	}

	/**
	 * A container's success/fail derived from the word rows in its subgraph,
	 * each word once — the in-memory form of the DAO's recursive sum. Plain
	 * links only, like the SQL: referenced content never counts.
	 */
	fun subgraphWordCounters(containerId: Int): Counters {
		var passed = 0
		var failed = 0
		val visited = HashSet<Int>()
		visited.add(containerId)
		val stack = ArrayDeque<Int>()
		stack.addLast(containerId)
		while (stack.isNotEmpty()) {
			val current = stack.removeLast()
			val row = rows[current]
			if (row?.type == ItemKind.WORD) {
				passed += row.passedTests
				failed += row.failedTests
			}
			val links = children[current]
			if (links != null) for (link in links)
				if (!link.isReference && visited.add(link.childId)) stack.addLast(link.childId)
		}
		return Counters(passed, failed)
	}

	// ------------------------------------------------------------ merge writes

	/**
	 * A container under a parent — the container half of the add merge rule.
	 * - file counters are never trusted — the counter phase derives them.
	 * - a top-level merge lazily loads the joined subject's scope.
	 * - the parent's plain children dedup — a reference shows content, it
	 *   holds no name in the parent.
	 */
	suspend fun insertContainer(parentId: Int, item: DbItem): DbItem {
		val existing = plainChildren(parentId)
			.firstOrNull { it.type == item.type && it.name == item.name }
		if (existing != null) {
			// a same-import placeholder has no scope to load — the flush
			// remaps it, so only a pre-existing row's scope matters here
			if (parentId == ROOT_ID && existing.id >= 0) loadScope(existing.id)
			return mergeDescription(existing, item.description)
		}
		val row = insert(item.copy(passedTests = 0, failedTests = 0))
		linkAtEnd(parentId, row.id)
		return row
	}

	// ------------------------------------------------------------ flush

	/**
	 * Writes everything the save phases collected — the new rows in one bulk
	 * insert (with `id = 0`, auto-generated), their links in another, then
	 * the merged descriptions of existing rows.
	 */
	suspend fun flush(): Map<Int, Int> {
		if (pending.isEmpty() && pendingLinks.isEmpty() && pendingDescriptions.isEmpty())
			return emptyMap()
		val remap = HashMap<Int, Int>()
		if (pending.isNotEmpty()) {
			val ids = dao.insertAll(pending.map { rows[it]!!.copy(id = 0) })
			pending.forEachIndexed { index, placeholder -> remap[placeholder] = ids[index].toInt() }
		}
		remapIndexes(remap)
		if (pendingLinks.isNotEmpty())
			dao.insertAllLinks(pendingLinks.map { remapLink(it, remap) })
		for (row in pendingDescriptions.values)
			dao.updateById(row.id, row.name, row.description)
		return remap
	}

	// ------------------------------------------------------------ internals

	private fun isRootChild(id: Int): Boolean =
		parents[id]?.any { it.parentId == ROOT_ID } == true

	/** Whether a scope sits among a row's ancestors (the row itself counts). */
	private fun reaches(scope: Int, rowId: Int): Boolean {
		if (scope == rowId) return true
		// the root's scope is its direct children — the walk would otherwise span
		// every subject, merging a bare root word with a subject's word
		if (scope == ROOT_ID) return childIds[ROOT_ID]?.contains(rowId) == true
		val visited = HashSet<Int>()
		visited.add(rowId)
		val queue = ArrayDeque<Int>()
		queue.add(rowId)
		while (queue.isNotEmpty()) {
			val links = parents[queue.removeFirst()] ?: continue
			for (link in links) {
				val parent = link.parentId
				if (parent == scope) return true
				if (visited.add(parent)) queue.add(parent)
			}
		}
		return false
	}

	/** Inserts [row] as a pending row under a negative placeholder id ([flush] remaps it). */
	override suspend fun insert(row: DbItem): DbItem {
		val placeholder = row.copy(id = nextPlaceholder--)
		pending.add(placeholder.id)
		indexRow(placeholder)
		return placeholder
	}

	/** Indexes a just-created row into the session's lookups. */
	private fun indexRow(row: DbItem) {
		rows[row.id] = row
		byName.getOrPut(row.name) { HashMap() }
			.getOrPut(row.type) { ArrayList() }.add(row.id)
	}

	/**
	 * Joins a description into a row — the row object updates in place (a later merge
	 * in the same import reads the joined value), the database write waits for the
	 * flush — nothing to write for a pending row, [pendingDescriptions] collects an
	 * existing one.
	 */
	override suspend fun mergeDescription(row: DbItem, incoming: String?): DbItem {
		val merged = Descriptions.merge(row.description, incoming)
		if (merged == row.description) return row
		val updated = row.copy(description = merged)
		rows[updated.id] = updated
		if (updated.id >= 0) pendingDescriptions[updated.id] = updated
		return updated
	}

	/** Writes a row's counters now — merged existing rows bypass the flush. */
	private suspend fun writeCounters(row: DbItem, passed: Int, failed: Int) {
		rows[row.id] = row.copy(passedTests = passed, failedTests = failed)
		if (row.id >= 0) dao.updateCounters(row.id, passed, failed)
	}

	override suspend fun mergeCounters(row: DbItem, passed: Int, failed: Int) {
		if (passed + failed > row.passedTests + row.failedTests) {
			adopted.add(row.id)
			writeCounters(row, passed, failed)
		}
	}

	override suspend fun linkAtEnd(parentId: Int, childId: Int) {
		val link = DbLink(parentId, childId, nextPosition(parentId))
		indexLink(link)
		pendingLinks.add(link)
	}

	/** Places a reference link of [target] under [parentId] at the end of its list. */
	fun addReference(parentId: Int, target: Int) {
		val link = DbLink(parentId, target, nextPosition(parentId), isReference = true)
		indexLink(link)
		pendingLinks.add(link)
	}

	private val loadedScopes = HashSet<Int>()

	/**
	 * Loads [scopeId]'s whole subgraph and the links touching it — once per
	 * scope. A second load (a scope shared with an already-loaded one) is a
	 * no-op.
	 */
	private suspend fun loadScope(scopeId: Int) {
		if (!loadedScopes.add(scopeId)) return
		for (row in dao.subgraph(scopeId)) indexLoadedRow(row)
		for (link in dao.subgraphChildLinks(scopeId)) indexLoadedLink(link)
		sortChildren()
	}

	/** The children lists must match the live per-node query's order. */
	private fun sortChildren() {
		for (list in children.values) {
			list.sortWith(compareBy({ it.position }, { it.childId }))
			claimNextPosition(list.first().parentId, list.last().position + 1)
		}
	}

	/** Indexes a loaded row — an already-known id (a shared scope) keeps its row. */
	private fun indexLoadedRow(row: DbItem) {
		if (rows.containsKey(row.id)) return
		rows[row.id] = row
		byName.getOrPut(row.name) { HashMap() }
			.getOrPut(row.type) { ArrayList() }.add(row.id)
	}

	/** Indexes a loaded link — an already-known link (a shared scope) is skipped. */
	private suspend fun indexLoadedLink(link: DbLink) {
		if (linked(link.parentId, link.childId)) return
		indexLink(link)
	}

	/** Records [link] in every adjacency index — the loads and the new links. */
	private fun indexLink(link: DbLink) {
		children.getOrPut(link.parentId) { ArrayList() }.add(link)
		childIds.getOrPut(link.parentId) { HashSet() }.add(link.childId)
		parents.getOrPut(link.childId) { ArrayList() }.add(link)
		claimNextPosition(link.parentId, link.position + 1)
	}

	/**
	 * The one writer of the next free position per parent — a max-merge, so
	 * the sorted load and the appended links agree on the end of the list.
	 */
	private fun claimNextPosition(parentId: Int, position: Int) {
		nextPositions[parentId] = maxOf(nextPositions[parentId] ?: 0, position)
	}

	/** Re-keys every id-bearing index onto the real ids of [remap]. */
	private fun remapIndexes(remap: Map<Int, Int>) {
		if (remap.isEmpty()) return
		for ((placeholder, real) in remap) {
			val row = rows.remove(placeholder)!!
			rows[real] = row.copy(id = real)
		}
		for (kinds in byName.values)
			for (ids in kinds.values)
				for (index in ids.indices)
					remap[ids[index]]?.let { ids[index] = it }
		// the adopted set feeds the counter phase — its ids must be real
		// by then, or the phase's row lookups skip them
		for (id in adopted.toList()) {
			remap[id]?.let {
				adopted.remove(id)
				adopted.add(it)
			}
		}
		remapLinksInto(children, remap)
		remapLinksInto(parents, remap)
		remapChildIds(remap)
		remapNextPositions(remap)
	}

	/** Rewrites the link objects and keys of [index] onto the real ids. */
	private fun remapLinksInto(index: HashMap<Int, ArrayList<DbLink>>, remap: Map<Int, Int>) {
		for ((key, links) in index.toList()) {
			links.replaceAll { remapLink(it, remap) }
			val real = remap[key]
			if (real != null) {
				index.getOrPut(real) { ArrayList() }.addAll(links)
				index.remove(key)
			}
		}
	}

	private fun remapChildIds(remap: Map<Int, Int>) {
		for (set in childIds.values)
			for (id in set.toList()) {
				val real = remap[id]
				if (real != null) {
					set.remove(id)
					set.add(real)
				}
			}
		for ((key, set) in childIds.toList()) {
			val real = remap[key] ?: continue
			childIds.getOrPut(real) { HashSet() }.addAll(set)
			childIds.remove(key)
		}
	}

	private fun remapNextPositions(remap: Map<Int, Int>) {
		for (key in nextPositions.keys.toList()) {
			val real = remap[key] ?: continue
			nextPositions[real] = maxOf(nextPositions[real] ?: 0, nextPositions.remove(key)!!)
		}
	}

	private fun remapLink(link: DbLink, remap: Map<Int, Int>): DbLink = DbLink(
		remap[link.parentId] ?: link.parentId,
		remap[link.childId] ?: link.childId,
		link.position, link.isReference)
}
