package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.TopPullAccumulator
import cz.litoj.schlmgr.ui.component.TopPullEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the pull fires once per gesture at the threshold.
 * - a drag the other way after a fire reports the reversal once.
 * - a reversed pull never fires again before the gesture ends.
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
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
		assertEquals(TopPullEvent.None, atTop(0.5f))
	}

	@Test
	fun `the pull fires once at the threshold, then stays quiet`() {
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
		assertEquals(TopPullEvent.Fired, atTop(1f))
		assertEquals("still pulled down", TopPullEvent.None, atTop(threshold))
	}

	@Test
	fun `a scroll the list can still take clears the pull`() {
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
		assertEquals(
			TopPullEvent.None,
			pull.scroll(threshold, canScrollBackward = true, drag = true),
		)
		assertEquals(TopPullEvent.None, atTop(1f))
	}

	@Test
	fun `a delta the other way clears the pull`() {
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
		assertEquals(TopPullEvent.None, atTop(-10f))
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
	}

	@Test
	fun `a scroll without the finger never fires`() {
		assertEquals(
			TopPullEvent.None,
			pull.scroll(threshold * 2f, canScrollBackward = false, drag = false),
		)
	}

	@Test
	fun `a reverse after the fire reports the reversal once`() {
		assertEquals(TopPullEvent.Fired, atTop(threshold))
		assertEquals(TopPullEvent.Reversed, atTop(-10f))
		assertEquals("one reversal per fire", TopPullEvent.None, atTop(-10f))
	}

	@Test
	fun `a pull after the reversal never fires again`() {
		assertEquals(TopPullEvent.Fired, atTop(threshold))
		assertEquals(TopPullEvent.Reversed, atTop(-10f))
		assertEquals(TopPullEvent.None, atTop(threshold - 1f))
		assertEquals("the fire stands for the gesture", TopPullEvent.None, atTop(1f))
	}

	@Test
	fun `a reversed fling never reports a reversal`() {
		assertEquals(TopPullEvent.Fired, atTop(threshold))
		assertEquals(
			TopPullEvent.None,
			pull.scroll(-10f, canScrollBackward = false, drag = false),
		)
		assertEquals("the fire stands for the finger", TopPullEvent.Reversed, atTop(-10f))
	}

	@Test
	fun `the next gesture starts from zero`() {
		assertEquals(TopPullEvent.Fired, atTop(threshold))
		pull.gestureEnded()
		assertEquals("the pull did not carry over", TopPullEvent.None, atTop(threshold - 1f))
		assertEquals(TopPullEvent.Fired, atTop(1f))
	}
}
