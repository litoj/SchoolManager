package cz.litoj.schlmgr.unit.ui.component

import androidx.compose.ui.geometry.Offset
import cz.litoj.schlmgr.ui.component.DoubleTapDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification of the second tap of a double by time and place.
 */
class DoubleTapDetectorTest {

	private val detector = DoubleTapDetector(spanPx = 32f, windowMs = 300L)

	@Test
	fun `two word taps in the window at one place form a double`() {
		assertFalse(detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = true))
		assertTrue(detector.onDown(Offset(110f, 105f), at = 1_200L, isWord = true))
	}

	@Test
	fun `a second tap outside the place stays single`() {
		detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = true)
		assertFalse(detector.onDown(Offset(200f, 100f), at = 1_200L, isWord = true))
	}

	@Test
	fun `a second tap outside the window stays single`() {
		detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = true)
		assertFalse(detector.onDown(Offset(105f, 100f), at = 1_400L, isWord = true))
	}

	@Test
	fun `a previous tap on another row kind stays single`() {
		detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = false)
		assertFalse(detector.onDown(Offset(100f, 100f), at = 1_200L, isWord = true))
	}

	@Test
	fun `the first tap of a list is single`() {
		assertFalse(detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = true))
	}

	@Test
	fun `a zero window never closes a double`() {
		val off = DoubleTapDetector(spanPx = 32f, windowMs = 0L)
		off.onDown(Offset(100f, 100f), at = 1_000L, isWord = true)
		assertFalse(off.onDown(Offset(100f, 100f), at = 1_001L, isWord = true))
	}

	@Test
	fun `a closed double hands over the anchor of the opening tap`() {
		detector.onDown(Offset(100f, 100f), at = 1_000L, isWord = true, anchor = 7)

		assertTrue(detector.onDown(Offset(110f, 105f), at = 1_200L, isWord = true, anchor = 9))
		assertEquals("the anchor rides the opening tap, not the closing one", 7, detector.openAnchor)
	}
}
