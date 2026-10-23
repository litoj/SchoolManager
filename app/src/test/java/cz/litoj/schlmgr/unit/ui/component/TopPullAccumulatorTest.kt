package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.TopPullAccumulator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the pull fires once past the threshold.
 * - it does not fire again until the gesture ends.
 * - scrolls the list can still take never fire.
 * - opposite deltas and non-drag scrolls never fire.
 */
class TopPullAccumulatorTest {

	private val threshold = 100f
	private val pull = TopPullAccumulator(threshold)

	/** One pull delta at the top: a pull down arrives positive. */
	private fun atTop(dy: Float) = pull.scroll(dy, canScrollBackward = false, drag = true)

	@Test
	fun `a drag under the threshold never fires`() {
		assertFalse(atTop(threshold - 1f))
		assertFalse(atTop(0.5f))
	}

	@Test
	fun `the pull fires once at the threshold, then stays quiet`() {
		assertFalse(atTop(threshold - 1f))
		assertTrue(atTop(1f))
		assertFalse("already fired this gesture", atTop(threshold))
	}

	@Test
	fun `a scroll the list can still take clears the pull`() {
		assertFalse(atTop(threshold - 1f))
		assertFalse(pull.scroll(threshold, canScrollBackward = true, drag = true))
		assertFalse(atTop(1f))
	}

	@Test
	fun `a delta the other way clears the pull`() {
		assertFalse(atTop(threshold - 1f))
		assertFalse(atTop(-10f))
		assertFalse(atTop(threshold - 1f))
	}

	@Test
	fun `a scroll without the finger never fires`() {
		assertFalse(pull.scroll(threshold * 2f, canScrollBackward = false, drag = false))
	}

	@Test
	fun `the next gesture starts from zero`() {
		assertTrue(atTop(threshold))
		pull.gestureEnded()
		assertFalse("the pull did not carry over", atTop(threshold - 1f))
		assertTrue(atTop(1f))
	}
}
