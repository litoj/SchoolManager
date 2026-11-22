package cz.litoj.schlmgr.unit.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.SuccessBand
import cz.litoj.schlmgr.ui.component.successRatioArgb
import cz.litoj.schlmgr.ui.component.successRatioBackground
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.StaticRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The success-ratio background tint, a low alpha over the plain colours.
 * - the red side deepens with failures once a row has enough tests.
 * - the alpha fades toward green, whose higher luminance lightens the band.
 */
class SuccessRatioArgbTest {

	@Test
	fun `empty and untested rows stay untinted`() {
		assertEquals(0, successRatioArgb(-2)) // empty container
		assertEquals(0, successRatioArgb(-1)) // never tested
	}

	@Test
	fun `ratios 0, 50 and 100 use the same gradient`() {
		assertEquals(0x80FF0022.toInt(), successRatioArgb(0, testedCount = 1, peak = 0x80))
		// 1 success + 1 fail: the hue ramp meets at full yellow and peak alpha
		assertEquals(0x80FFFF22.toInt(), successRatioArgb(50, testedCount = 2, failedTests = 1, peak = 0x80))
		assertEquals(0x4000FF22, successRatioArgb(100, testedCount = 1, peak = 0x80))
	}

	@Test
	fun `red holds peak alpha and green fades it linearly`() {
		assertEquals(0x80, successRatioArgb(0, testedCount = 1, peak = 0x80) ushr 24 and 0xFF)
		assertEquals(0x80, successRatioArgb(49, testedCount = 2, failedTests = 1, peak = 0x80) ushr 24 and 0xFF)
		assertEquals(0x80, successRatioArgb(50, testedCount = 2, failedTests = 1, peak = 0x80) ushr 24 and 0xFF)
		assertEquals(0x60, successRatioArgb(75, testedCount = 2, failedTests = 1, peak = 0x80) ushr 24 and 0xFF)
		assertEquals(0x40, successRatioArgb(100, testedCount = 1, peak = 0x80) ushr 24 and 0xFF)
	}

	@Test
	fun `below 50 grades the green channel`() {
		// red stays full, the green ramp rises, the low byte 22, alpha 0x80
		assertEquals(
			0x80FF0000.toInt() or (25 * 256 / 50 shl 8) or 0x22,
			successRatioArgb(25, testedCount = 2, failedTests = 1, peak = 0x80),
		)
	}

	@Test
	fun `above 50 grades the red channel`() {
		// the high byte from the inverted ratio, then FF22, at alpha 0x60
		val grade = (100 - 75) * 256 / 50 // 128 = 0x80
		val argb = successRatioArgb(75, testedCount = 2, failedTests = 1, peak = 0x80)
		assertEquals(grade, argb ushr 16 and 0xFF) // red carries the grade
		assertEquals(0x6080FF22, argb)
	}

	@Test
	fun `fewer than three tests keep the peak alpha`() {
		assertEquals(0x80, successRatioArgb(0, testedCount = 2, failedTests = 2, peak = 0x80) ushr 24 and 0xFF)
	}

	@Test
	fun `three tests add a tenth of the peak per failure`() {
		assertEquals(0x4C, successRatioArgb(0, testedCount = 3, failedTests = 2, peak = 0x40) ushr 24 and 0xFF)
		assertEquals(0x60, successRatioArgb(0, testedCount = 5, failedTests = 5, peak = 0x40) ushr 24 and 0xFF)
	}

	@Test
	fun `the failure step never passes full alpha`() {
		assertEquals(0xFF, successRatioArgb(0, testedCount = 3, failedTests = 10, peak = 0x80) ushr 24 and 0xFF)
	}

	@Test
	fun `a chapter or subject failure adds a hundredth of the peak`() {
		val peak = SuccessBand.peakAlpha()
		fun alphaOf(kind: RowKind): Int {
			val row = StaticRow(
				key = Any(), position = 1, name = "x", kind = kind,
				ratio = 0, testedCount = 30, failedTests = 10,
			)
			return successRatioBackground(row).toArgb() ushr 24 and 0xFF
		}
		assertEquals(peak + 10 * peak / 100, alphaOf(RowKind.CHAPTER))
		assertEquals(peak + 10 * peak / 100, alphaOf(RowKind.SUBJECT))
	}

	@Test
	fun `the peak scales both sides of the ramp`() {
		assertEquals(0x40, successRatioArgb(0, testedCount = 1, peak = 0x40) ushr 24 and 0xFF)
		assertEquals(0x40, successRatioArgb(50, testedCount = 2, failedTests = 1, peak = 0x40) ushr 24 and 0xFF)
		assertEquals(0x20, successRatioArgb(100, testedCount = 1, peak = 0x40) ushr 24 and 0xFF)
	}

	@Test
	fun `a reference row renders the success-ratio background like any other`() {
		val row = StaticRow(
			key = Any(), position = 1, name = "x", description = "", kind = RowKind.CHAPTER,
			ratio = 25, testedCount = 2, failedTests = 1, isReference = true, hasSource = true, selected = false,
		)
		assertEquals(
			Color(successRatioArgb(25, testedCount = 2, failedTests = 1, peak = SuccessBand.peakAlpha())),
			successRatioBackground(row),
		)
	}
}
