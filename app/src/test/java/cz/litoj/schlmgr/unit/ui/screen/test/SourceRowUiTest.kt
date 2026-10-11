package cz.litoj.schlmgr.unit.ui.screen.test

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.toSourceRowUi
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of the source-row mapper ([toSourceRowUi]): the kind follows the
 * row the item was picked as (a chain of length 1 is a whole subject, anything
 * else by its last item's type), and a plain name maps through unchanged.
 */
class SourceRowUiTest {

	private fun item(name: String, type: ItemKind) =
		DbItem(id = 1, name = name, description = "", type = type)

	@Test
	fun `a whole-subject chain is a subject row`() {
		val row = listOf(item("AJ-C1", ItemKind.CHAPTER)).toSourceRowUi()
		assertEquals(RowKind.SUBJECT, row.kind)
	}

	@Test
	fun `a chain ending in a word is a word row`() {
		val chain = listOf(item("AJ-C1", ItemKind.CHAPTER), item("das Haus", ItemKind.WORD))
		assertEquals(RowKind.WORD, chain.toSourceRowUi().kind)
	}

	@Test
	fun `a chain ending in a chapter is a chapter row`() {
		val chain = listOf(item("AJ-C1", ItemKind.CHAPTER), item("Integrals", ItemKind.CHAPTER))
		assertEquals(RowKind.CHAPTER, chain.toSourceRowUi().kind)
	}

	@Test
	fun `the display name is the last item's parsed name`() {
		val chain = listOf(item("AJ-C1", ItemKind.CHAPTER), item("Integrals", ItemKind.CHAPTER))
		assertEquals("Integrals", chain.toSourceRowUi().displayName)
	}
}
