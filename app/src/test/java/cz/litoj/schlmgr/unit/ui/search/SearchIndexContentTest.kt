package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The content of seed rows, the scope a scoped search reports.
 * - a seed reaches its whole subtree, so a marked chapter searches its words.
 * - a shared row is named once, however many seeds reach it.
 */
class SearchIndexContentTest {

	private fun item(id: Int, kind: ItemKind) =
		DbItem(id = id, name = "i$id", description = "", type = kind)

	private fun link(parent: Int, child: Int, position: Int = 0) =
		DbLink(parentId = parent, childId = child, position = position)

	/** root holds the chapter and the loose word, the chapter holds a word with a translation. */
	private val tree = SearchIndex(
		rows = listOf(
			item(1, ItemKind.CHAPTER), item(2, ItemKind.CHAPTER), item(3, ItemKind.WORD),
			item(4, ItemKind.TRANSLATION), item(5, ItemKind.WORD),
		),
		links = listOf(link(1, 2), link(1, 5, 1), link(2, 3), link(3, 4)),
	)

	@Test
	fun `a chapter seed reaches its words and their translations`() {
		assertEquals(setOf(2, 3, 4), tree.contentOf(listOf(2)))
	}

	@Test
	fun `a word seed reaches its translations only`() {
		assertEquals(setOf(3, 4), tree.contentOf(listOf(3)))
	}

	@Test
	fun `seeds reach every subtree once`() {
		assertEquals(setOf(2, 3, 4, 5), tree.contentOf(listOf(2, 5)))
	}

	@Test
	fun `no seed means no content`() {
		// an empty scope never reaches here, the run reads the whole container instead
		assertTrue(tree.contentOf(emptyList()).isEmpty())
	}

	@Test
	fun `a row the index never held still counts as its own content`() {
		assertEquals(setOf(9), tree.contentOf(listOf(9)))
	}

	@Test
	fun `linked rows end the walk`() {
		val ring = SearchIndex(
			rows = listOf(item(1, ItemKind.CHAPTER), item(2, ItemKind.CHAPTER)),
			links = listOf(link(1, 2), link(2, 1)),
		)
		assertEquals(setOf(1, 2), ring.contentOf(listOf(1)))
	}
}
