package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The flipped-word display assembly ([ItemRow.flippedDisplay]). */
class ItemRowFlipTest {

	private fun trl(name: String, description: String = "") =
		DbItem(name = name, description = description, type = ItemKind.TRANSLATION)

	@Test
	fun `each translation is one name line carrying its description`() {
		val display = ItemRow.flippedDisplay(listOf(trl("ano"), trl("yes", "formal")))
		assertEquals("ano\nyes", display.names)
		assertEquals("formal", display.info)
		assertEquals(2, display.lines.size)
		assertEquals("ano", display.lines[0].name)
		assertEquals("", display.lines[0].description)
		assertEquals("yes", display.lines[1].name)
		assertEquals("formal", display.lines[1].description)
	}

	@Test
	fun `several descriptions are prefixed with their translation name`() {
		val display = ItemRow.flippedDisplay(listOf(trl("ano", "neuter"), trl("yes", "formal")))
		assertEquals("ano: neuter\nyes: formal", display.info)
	}

	@Test
	fun `a word without translations renders an empty flip`() {
		// a mis-anchored import can leave a word row without translations —
		// the flip must not crash on the empty list
		val display = ItemRow.flippedDisplay(emptyList())
		assertEquals("", display.names)
		assertEquals("", display.info)
		assertEquals(0, display.lines.size)
	}
}
