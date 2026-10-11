package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink

/**
 * One container's whole content, preloaded for a search run — rows, per-parent
 * children in link order, and every incoming link (outside parents included).
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

	/** Children of a container in link order — chapters, or a word's translations. */
	fun childrenOf(parentId: Int): List<DbItem> = children[parentId] ?: emptyList()

	/**
	 * Every link into an item — its whole parent set with the reference flags.
	 * A hit row's reference facts come from these, so building thousands of
	 * hit rows costs no per-row database round-trip.
	 */
	fun linksOf(childId: Int): List<DbLink> = intoChild[childId] ?: emptyList()
}
