package cz.litoj.schlmgr.ui.explorer.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the success-ratio gradient ([successRatioArgb]). Every ratio uses the
 * one alpha `0x40`, so the tints have the same, partial transparency. The values must
 * match `SearchAdapter.background(int)` except for that single alpha: in particular the
 * above-50 grade goes into the RED channel. The legacy special colours for 0, 50 and 100
 * are gone (they had a higher saturation and another alpha — 50 % was a bright opaque
 * yellow), as are the stronger `0x60` alpha above 50 % and the `0x80` on the unrated blue.
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
		// legacy "FF" + hex(sf*256/50) + "22", alpha 0x40
		assertEquals(0x40FF0000 or (25 * 256 / 50 shl 8) or 0x22, successRatioArgb(25))
		assertEquals(0x40, successRatioArgb(25) ushr 24 and 0xFF)
	}

	@Test
	fun `above 50 grades the red channel and keeps the 0x40 alpha`() {
		// legacy hex((100-sf)*256/50) + "FF22", alpha 0x40 (not the legacy 0x60)
		val grade = (100 - 75) * 256 / 50 // 128 = 0x80
		assertEquals(0x40, successRatioArgb(75) ushr 24 and 0xFF) // alpha, not the grade
		assertEquals(grade, successRatioArgb(75) ushr 16 and 0xFF) // red carries the grade
		assertEquals(0x4080FF22, successRatioArgb(75))
	}
}
