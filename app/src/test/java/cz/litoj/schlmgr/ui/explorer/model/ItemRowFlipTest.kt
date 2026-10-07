package cz.litoj.schlmgr.ui.explorer.model

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
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
}
