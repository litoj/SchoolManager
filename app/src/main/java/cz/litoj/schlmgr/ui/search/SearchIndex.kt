package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink

/**
 * The whole content of one container, preloaded for a search run.
 * - rows and per-parent children in link order.
 * - every incoming link, outside parents included.
 */
class SearchIndex(rows: List<DbItem>, links: List<DbLink>, childLinks: List<DbLink> = emptyList()) {

	private val byId: Map<Int, DbItem> = rows.associateBy { it.id }
	private val children: Map<Int, List<DbItem>>

	/** The links into a child id, with parents outside the subgraph included. */
	private val intoChild: Map<Int, List<DbLink>>

	init {
		children = links
			.sortedBy { it.position }
			.groupBy({ it.parentId }, { it.childId })
			.mapValues { (_, childIds) -> childIds.mapNotNull(byId::get) }
		intoChild = childLinks.groupBy { it.childId }
	}

	/** The children of a container in link order, chapters or the translations of a word. */
	fun childrenOf(parentId: Int): List<DbItem> = children[parentId] ?: emptyList()

	/**
	 * Every link into an item, its whole parent set with the reference flags.
	 * - the reference facts of a hit row come from these.
	 * - so building thousands of hit rows costs no per-row database round-trip.
	 */
	fun linksOf(childId: Int): List<DbLink> = intoChild[childId] ?: emptyList()
}
