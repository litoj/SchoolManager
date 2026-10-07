package cz.litoj.schlmgr.ui.explorer.search

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.DbLink

/**
 * The whole content of one container, preloaded for a search run: every item row
 * reachable below the container plus the parent→child links between those rows,
 * resolved into per-parent children lists (kept in link order).
 *
 * The search used to walk the tree one database query per chapter; it now loads
 * the whole subgraph once ([ItemRepository.subgraph] + [ItemRepository.subgraphLinks])
 * and filters this in-memory structure. The rows are plain `item` records of every
 * type, so the name and description filters apply to all of them uniformly — no
 * type-specific parsing.
 */
class SearchIndex(rows: List<DbItem>, links: List<DbLink>) {

	private val byId: Map<Int, DbItem> = rows.associateBy { it.id }
	private val children: Map<Int, List<DbItem>>

	init {
		children = links
			.sortedBy { it.position }
			.groupBy({ it.parentId }, { it.childId })
			.mapValues { (_, childIds) -> childIds.mapNotNull(byId::get) }
	}

	/**
	 * The children of [parentId] in their link order — chapters of a container,
	 * or the translations of a word.
	 */
	fun childrenOf(parentId: Int): List<DbItem> = children[parentId] ?: emptyList()
}
