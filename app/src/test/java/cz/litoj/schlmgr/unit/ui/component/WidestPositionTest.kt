package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.widestPosition
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [widestPosition], the position whose text takes the most
 * horizontal space among 1..maxPosition.
 * - the tie goes to the largest of equally wide positions.
 */
class WidestPositionTest {

	@Test
	fun `a list without positions falls back to the first`() {
		assertEquals(1, widestPosition(0) { 10 })
	}

	@Test
	fun `equal digit widths keep the largest position`() {
		// no number below the max can tie its length, and equal widths cannot beat it
		assertEquals(7, widestPosition(7) { 10 })
		assertEquals(42, widestPosition(42) { 10 })
		assertEquals(1000, widestPosition(1000) { 10 })
	}

	@Test
	fun `a wide digit below the limit wins within the same length`() {
		// only 4 is wide, so 44 with 40 beats 49 with 25
		assertEquals(44, widestPosition(49) { if (it == 4) 20 else 5 })
	}

	@Test
	fun `a short number wins when the leading digits are narrow`() {
		// 1 and 0 are narrow, so 999 with 60 beats 1000 with 8 even though it is shorter
		assertEquals(999, widestPosition(1000) { if (it < 2) 2 else 20 })
	}

	@Test
	fun `matches the brute force over every position`() {
		val random = Random(7)
		repeat(50) {
			val widths = IntArray(10) { random.nextInt(3, 30) }
			val max = random.nextInt(1, 2000)
			fun width(n: Int) = n.toString().sumOf { widths[it - '0'] }
			val widest = (1..max).maxOf(::width)
			// the largest of the equally wide ones, the documented tie-break
			val expected = (1..max).filter { width(it) == widest }.max()
			assertEquals(expected, widestPosition(max) { widths[it] })
		}
	}

	/**
	 * Exhaustive cross-check of every N in 1..3000 under seven width profiles.
	 * - the brute force runs incrementally, with one width per N.
	 * - the whole table thus costs milliseconds.
	 * - it checks the tie-break too, the largest of equally wide positions.
	 */
	@Test
	fun `matches brute force for every position up to 3000`() {
		val random11 = Random(11)
		val random42 = Random(42)
		val profiles = listOf(
			"tabular" to IntArray(10) { 10 },
			"dip4" to IntArray(10) { if (it == 4) 20 else 5 },
			"shortWins" to IntArray(10) { if (it < 2) 2 else 20 },
			"zeroWidest" to IntArray(10) { if (it == 0) 20 else 5 },
			"nineDominant" to IntArray(10) { if (it == 9) 30 else 3 },
			"random11" to IntArray(10) { random11.nextInt(2, 30) },
			"random42" to IntArray(10) { random42.nextInt(2, 40) },
		)
		for ((name, widths) in profiles) {
			var bestWidth = -1
			var bestN = 1
			for (n in 1..3000) {
				val w = n.toString().sumOf { widths[it - '0'] }
				if (w > bestWidth || (w == bestWidth && n > bestN)) {
					bestWidth = w
					bestN = n
				}
				assertEquals("$name@$n", bestN, widestPosition(n) { widths[it] })
			}
		}
	}
}
