package cz.litoj.schlmgr.unit.ui.component

import androidx.compose.ui.graphics.Color
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.successRatioArgb
import cz.litoj.schlmgr.ui.component.successRatioBackground
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The success-ratio background tint — a fixed low alpha over the plain
 * success colours at 50/100 and the unrated/empty sentinels.
 */
class SuccessRatioArgbTest {

	@Test
	fun `empty and unrated items keep their values`() {
		assertEquals(0, successRatioArgb(-2)) // empty container: no colour at all
		assertEquals(0x401A6AB8, successRatioArgb(-1)) // unrated: translucent blue
	}

	@Test
	fun `ratios 0, 50 and 100 use the same gradient`() {
		assertEquals(0x40FF0022, successRatioArgb(0)) // same saturation and alpha as the rest
		assertEquals(0x40FFFF22, successRatioArgb(50)) // 1 success + 1 fail: translucent yellow
		assertEquals(0x4000FF22, successRatioArgb(100))
	}

	@Test
	fun `below 50 grades the green channel at a 0x40 alpha`() {
		// "FF" + hex(sf*256/50) + "22", alpha 0x40
		assertEquals(0x40FF0000 or (25 * 256 / 50 shl 8) or 0x22, successRatioArgb(25))
		assertEquals(0x40, successRatioArgb(25) ushr 24 and 0xFF)
	}

	@Test
	fun `above 50 grades the red channel and keeps the 0x40 alpha`() {
		// hex((100-sf)*256/50) + "FF22" at alpha 0x40
		val grade = (100 - 75) * 256 / 50 // 128 = 0x80
		assertEquals(0x40, successRatioArgb(75) ushr 24 and 0xFF) // alpha, not the grade
		assertEquals(grade, successRatioArgb(75) ushr 16 and 0xFF) // red carries the grade
		assertEquals(0x4080FF22, successRatioArgb(75))
	}

	@Test
	fun `a reference row renders the success-ratio background like any other`() {
		val row = ItemUiModel(
			key = Any(), position = 1, name = "x", description = "", kind = RowKind.CHAPTER,
			ratio = 25, isReference = true, hasSource = true, selected = false,
			payload = ItemRow(DbItem(name = "x", description = "", type = ItemKind.CHAPTER), null, 1),
		)
		assertEquals(Color(successRatioArgb(25)), successRatioBackground(row))
	}
}
