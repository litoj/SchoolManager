package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of [SearchIndex.linksOf]: a hit row's reference facts come from
 * the preloaded child links (one query per run), including parents outside
 * the searched subgraph — a reference placed elsewhere still points at a
 * subgraph item, and its jump-to-source must survive the preload.
 */
class SearchIndexLinksTest {

	private fun item(id: Int) = DbItem(id = id, name = "i$id", description = "", type = ItemKind.WORD)

	private fun link(parent: Int, child: Int, reference: Boolean = false) =
		DbLink(parentId = parent, childId = child, isReference = reference)

	@Test
	fun `linksOf returns every link into the child, outside parents included`() {
		val index = SearchIndex(
			rows = listOf(item(1), item(2)),
			links = listOf(link(1, 2)),
			childLinks = listOf(link(1, 2), link(9, 2, reference = true)), // 9 lies outside the subgraph
		)
		assertEquals(listOf(1, 9), index.linksOf(2).map { it.parentId })
	}

	@Test
	fun `linksOf keeps the reference flag of each link`() {
		val index = SearchIndex(
			rows = listOf(item(1), item(2)),
			links = emptyList(),
			childLinks = listOf(link(1, 2), link(9, 2, reference = true)),
		)
		assertEquals(listOf(false, true), index.linksOf(2).map { it.isReference })
	}

	@Test
	fun `linksOf is empty for a child without links`() {
		val index = SearchIndex(listOf(item(3)), emptyList(), emptyList())
		assertTrue(index.linksOf(3).isEmpty())
	}
}
