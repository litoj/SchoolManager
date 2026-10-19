package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.paletteHover
import cz.litoj.schlmgr.ui.component.paletteOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure geometry of the New button's palette ([paletteOffset],
 * [paletteHover]) — the icons fan from straight up to straight left, and
 * a drag that left the button hovers the icon of its direction, however
 * short.
 */
class PaletteMathTest {

	@Test
	fun `the icons fan from straight up to straight left`() {
		val up = paletteOffset(0, 3, 100f)
		assertEquals(0f, up.x, 0.01f)
		assertEquals(-100f, up.y, 0.01f)
		val left = paletteOffset(2, 3, 100f)
		assertEquals(-100f, left.x, 0.01f)
		assertEquals(0f, left.y, 0.01f)
		// one icon sits centered on the arc
		val single = paletteOffset(0, 1, 100f)
		assertEquals(0f, single.x, 0.01f)
		assertEquals(-100f, single.y, 0.01f)
	}

	@Test
	fun `the hover picks the icon of the direction`() {
		// straight at the icons
		assertEquals(0, paletteHover(0f, -100f, 3, 10f))
		assertEquals(2, paletteHover(-100f, 0f, 3, 10f))
		// between two icons, the nearer wins
		assertEquals(1, paletteHover(-70.7f, -70.7f, 3, 10f))
	}

	@Test
	fun `a drag past half the button hovers by direction`() {
		// a step past half the button toward the diagonal icon
		assertEquals(1, paletteHover(-25f, -25f, 3, 25f))
		// a step past half the button up
		assertEquals(0, paletteHover(0f, -26f, 3, 25f))
	}

	@Test
	fun `the middle icon claims two thirds of each gap`() {
		// the geometric middle between chapter and word opens the word
		assertEquals(1, paletteHover(-38f, -92f, 3, 25f))
		// the geometric middle between word and note opens the word
		assertEquals(1, paletteHover(-92f, -38f, 3, 25f))
		// past the two-thirds zone the corner icons take over
		assertEquals(1, paletteHover(-34f, -94f, 3, 25f))
		assertEquals(2, paletteHover(-97f, -24f, 3, 25f))
	}

	@Test
	fun `a drag along the bottom points at the leftmost icon`() {
		// left, slightly below the press point
		assertEquals(2, paletteHover(-100f, 20f, 3, 25f))
		// down-left, well below the fan
		assertEquals(2, paletteHover(-100f, 100f, 3, 25f))
		// straight down — nearer to the left icon than to the up one
		assertEquals(2, paletteHover(0f, 100f, 3, 25f))
	}

	@Test
	fun `a drag under half the button is no direction`() {
		assertEquals(-1, paletteHover(0f, 0f, 3, 25f))
		assertEquals(-1, paletteHover(5f, -5f, 3, 25f))
		assertEquals(-1, paletteHover(-15f, -15f, 3, 25f))
	}

	@Test
	fun `every direction past the threshold hovers its nearest icon`() {
		// to the right — the up icon is the nearest of the fan
		assertEquals(0, paletteHover(100f, 0f, 3, 25f))
		// up-right
		assertEquals(0, paletteHover(70.7f, -70.7f, 3, 25f))
	}
}
