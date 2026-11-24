package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRowFlipTest {

	private fun trl(name: String, description: String = "") =
		DbItem(name = name, description = description, type = ItemKind.TRANSLATION)

	@Test
	fun `each translation is one name line carrying its description`() {
		val display = ItemRow.flippedDisplay(listOf(trl("ano"), trl("yes", "formal")))
		assertEquals("ano\nyes", display.names)
		assertEquals(2, display.lines.size)
		assertEquals("ano", display.lines[0].name)
		assertEquals("", display.lines[0].description)
		assertEquals("yes", display.lines[1].name)
		assertEquals("formal", display.lines[1].description)
	}

	@Test
	fun `a word without translations renders an empty flip`() {
		// a mis-anchored import can leave a word row without translations, so the
		// flip must not crash on the empty list
		val display = ItemRow.flippedDisplay(emptyList())
		assertEquals("", display.names)
		assertEquals(0, display.lines.size)
	}

	@Test
	fun `an older flip completion cannot undo a newer target`() {
		val row = ItemRow(DbItem(name = "word", description = "", type = ItemKind.WORD), null, 1, false)
		val older = checkNotNull(row.reserveFlip(true))
		val newer = checkNotNull(row.reserveFlip(false))

		assertTrue(row.completeFlip(newer, listOf(trl("new")), false))
		assertFalse(row.completeFlip(older, listOf(trl("old")), true))
		assertFalse(row.flipped)
	}

	@Test
	fun `fresh content invalidates a pending flip`() {
		val word = DbItem(name = "word", description = "", type = ItemKind.WORD)
		val row = ItemRow(word, null, 1, false)
		val pending = checkNotNull(row.reserveFlip(true))

		row.applyFresh(word.copy(name = "edited"), listOf(trl("new")))

		assertFalse(row.completeFlip(pending, listOf(trl("old")), true))
		assertEquals("edited", row.toShow)
		assertFalse(row.flipped)
	}
}
