package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.scrollStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure auto-scroll ramp ([scrollStep]).
 * - no step away from either edge — inside the edge zone the step grows
 *   with the finger's depth, capped at the given maximum.
 * - on an overlapping viewport the nearer edge wins, with an exact tie
 *   scrolling up.
 * - select auto-scroll is not covered — it lives in
 *   [rememberListDragModifier], so it needs a composed list.
 */
class ScrollStepTest {

	private val zone = 100f
	private val maxStep = 10f
	private val height = 1000f

	private fun step(y: Float): Float = scrollStep(y, height, zone, maxStep)

	@Test
	fun `away from either edge there is no step`() {
		assertEquals(0f, step(zone), 0f)
		assertEquals(0f, step(height / 2f), 0f)
		assertEquals(0f, step(height - zone), 0f)
	}

	@Test
	fun `the deeper into the top edge zone, the faster the list scrolls up`() {
		assertEquals(0f, step(zone), 0f)
		assertEquals(-maxStep / 2f, step(zone / 2f), 0.001f)
		assertEquals(-maxStep, step(0f), 0f)
	}

	@Test
	fun `the deeper into the bottom edge zone, the faster the list scrolls down`() {
		assertEquals(0f, step(height - zone), 0f)
		assertEquals(maxStep / 2f, step(height - zone / 2f), 0.001f)
		assertEquals(maxStep, step(height), 0f)
	}

	@Test
	fun `a finger held past the edge still steps at the maximum`() {
		assertEquals(-maxStep, step(-20f), 0f)
		assertEquals(maxStep, step(height + 20f), 0f)
	}

	/** A viewport shorter than both zones keeps the lean direction — nearer edge wins. */
	@Test
	fun `an overlapping viewport follows the nearer edge`() {
		val small = 150f
		fun smallStep(y: Float): Float = scrollStep(y, small, zone, maxStep)
		assertEquals(-maxStep * 0.4f, smallStep(60f), 0.001f)
		assertEquals(maxStep * 0.4f, smallStep(90f), 0.001f)
	}

	/** An exact tie in an overlapping viewport keeps scrolling up. */
	@Test
	fun `an overlapping tie scrolls up`() {
		val small = 150f
		fun smallStep(y: Float): Float = scrollStep(y, small, zone, maxStep)
		assertEquals(-maxStep * 0.25f, smallStep(small / 2f), 0.001f)
	}
}
