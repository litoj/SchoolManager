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
 * The success-ratio background tint, a low alpha over the plain colours.
 * - the middle of the ramp lifts the alpha, yellow reads fainter than red
 * or green at one alpha.
 */
class SuccessRatioArgbTest {

	@Test
	fun `empty and unrated items keep their values`() {
		assertEquals(0, successRatioArgb(-2)) // empty container: no colour at all
		assertEquals(0x401A6AB8, successRatioArgb(-1)) // unrated: translucent blue
	}

	@Test
	fun `ratios 0, 50 and 100 use the same gradient`() {
		assertEquals(0x40FF0022, successRatioArgb(0)) // full red keeps the base alpha
		// 1 success + 1 fail: yellow dims only the blue channel over a light
		// ground, so it needs the double alpha to read as strong as the ends
		assertEquals(0x80FFFF22.toInt(), successRatioArgb(50))
		assertEquals(0x4000FF22, successRatioArgb(100))
	}

	@Test
	fun `the alpha lifts linearly toward the middle`() {
		assertEquals(0x60, successRatioArgb(25) ushr 24 and 0xFF)
		assertEquals(0x60, successRatioArgb(75) ushr 24 and 0xFF)
		// one step from the middle stays under the peak
		assertEquals(0x7E, successRatioArgb(49) ushr 24 and 0xFF)
	}

	@Test
	fun `below 50 grades the green channel`() {
		// the high byte from the success ratio, the low byte 22, alpha 0x60
		assertEquals(0x60FF0000 or (25 * 256 / 50 shl 8) or 0x22, successRatioArgb(25))
	}

	@Test
	fun `above 50 grades the red channel`() {
		// the high byte from the inverted ratio, then FF22, at alpha 0x60
		val grade = (100 - 75) * 256 / 50 // 128 = 0x80
		assertEquals(grade, successRatioArgb(75) ushr 16 and 0xFF) // red carries the grade
		assertEquals(0x6080FF22, successRatioArgb(75))
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
