package cz.litoj.schlmgr.integration.ui.shell

import android.graphics.Color
import cz.litoj.schlmgr.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.pow

/**
 * - the icons are white.
 * - the band is the primary green darkened far enough for them to read.
 * - the two facts belong together, because a light band at night washes out the icons.
 * - the contrast is the WCAG ratio, where 4.5 is the AA threshold.
 * - white on the band clears it in both palettes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusBarThemeTest {

	private val app get() = RuntimeEnvironment.getApplication()

	private fun bandColor(): Int = app.getColor(R.color.colorStatusBar)

	/** The header green the band is derived from. */
	private fun headerColor(): Int = app.getColor(R.color.colorPrimaryHeader)

	/** The WCAG relative luminance of an sRGB colour, from 0 for black to 1 for white. */
	private fun luminance(color: Int): Double {
		fun channel(v: Int): Double {
			val s = v / 255.0
			return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
		}
		return 0.2126 * channel(Color.red(color)) +
			0.7152 * channel(Color.green(color)) +
			0.0722 * channel(Color.blue(color))
	}

	/** The WCAG contrast ratio of two colours, from 1 for equal to 21 for black on white. */
	private fun contrast(a: Int, b: Int): Double {
		val la = luminance(a)
		val lb = luminance(b)
		return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
	}

	private fun assertWhiteOnDarkenedPrimary() {
		val contrast = contrast(Color.WHITE, bandColor())
		assertTrue("white must read on the band (was $contrast)", contrast >= 4.5)
		assertTrue(
			"the band must be a darkened primary",
			luminance(bandColor()) < luminance(headerColor()),
		)
	}

	@Test
	fun `the light status band takes white icons`() {
		assertWhiteOnDarkenedPrimary()
	}

	@Test
	@Config(qualifiers = "night")
	fun `the night status band takes white icons too`() {
		assertWhiteOnDarkenedPrimary()
	}
}
