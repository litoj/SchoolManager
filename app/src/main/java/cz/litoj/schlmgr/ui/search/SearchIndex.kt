package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.DbLink

/**
 * The whole content of one container, preloaded for a search run: every item row
 * reachable below the container, the parent→child links between those rows
 * (resolved into per-parent children lists, kept in link order), and every link
 * *into* those rows (each item's whole parent set, parents outside the subgraph
 * included).
 *
 * The search used to walk the tree one database query per chapter; it now loads
 * the whole subgraph once ([ItemRepository.subgraph] + [ItemRepository.subgraphLinks]
 * + [ItemRepository.subgraphChildLinks]) and filters this in-memory structure.
 * The rows are plain `item` records of every type, so the name and description
 * filters apply to all of them uniformly — no type-specific parsing.
 */
class SearchIndex(rows: List<DbItem>, links: List<DbLink>, childLinks: List<DbLink> = emptyList()) {

	private val byId: Map<Int, DbItem> = rows.associateBy { it.id }
	private val children: Map<Int, List<DbItem>>

	/** child id → every link into it, parents outside the subgraph included. */
	private val intoChild: Map<Int, List<DbLink>>

	init {
		children = links
			.sortedBy { it.position }
			.groupBy({ it.parentId }, { it.childId })
			.mapValues { (_, childIds) -> childIds.mapNotNull(byId::get) }
		intoChild = childLinks.groupBy { it.childId }
	}

	/**
	 * The children of [parentId] in their link order — chapters of a container,
	 * or the translations of a word.
	 */
	fun childrenOf(parentId: Int): List<DbItem> = children[parentId] ?: emptyList()

	/**
	 * Every link into [childId] — the item's whole parent set with the
	 * reference flags. A hit row's reference facts (the row's `isReference` /
	 * `hasSource`) come from these, so building thousands of hit rows costs no
	 * per-row database round-trip.
	 */
	fun linksOf(childId: Int): List<DbLink> = intoChild[childId] ?: emptyList()
}
