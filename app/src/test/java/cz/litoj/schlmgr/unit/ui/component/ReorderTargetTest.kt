package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.RowGeometry
import cz.litoj.schlmgr.ui.component.reorderTargetAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * - a boundary at the edge of a moving row names no target, so that drop changes nothing.
 * - every other boundary keeps the plain insertion count of the remaining rows.
 */
class ReorderTargetTest {

	/** Four rows of 100 at 0, 100, 200 and 300, so their middles sit at 50, 150, 250 and 350. */
	private fun rows(): List<RowGeometry> =
		List(4) { RowGeometry(index = it, offset = it * 100, size = 100) }

	private fun target(y: Float, selected: List<Int> = listOf(1), rowCount: Int = 4): Int? =
		reorderTargetAt(rows(), selected, y, rowCount)

	@Test
	fun `a finger over a moving row names no target`() {
		assertNull(target(140f))
		assertNull(target(190f))
	}

	@Test
	fun `the run's own slot above it names no target`() {
		assertNull(target(90f))
	}

	@Test
	fun `the run's own slot below it names no target`() {
		assertNull(target(210f))
	}

	@Test
	fun `a slot past the own row names that slot`() {
		assertEquals(0, target(40f))
		assertEquals(2, target(290f))
	}

	@Test
	fun `a boundary beside a hole of the run names no target`() {
		// rows 0 and 2 move together, so the hole row has the run on both its edges
		assertNull(target(110f, selected = listOf(0, 2)))
		assertNull(target(190f, selected = listOf(0, 2)))
	}

	@Test
	fun `a boundary past the hole of the run names that slot`() {
		assertEquals(2, target(390f, selected = listOf(0, 2)))
	}

	@Test
	fun `the row below a run at the list tail names no target`() {
		// row 3 is the last row, so no boundary below it crosses anything
		assertNull(target(290f, selected = listOf(3)))
	}

	@Test
	fun `a run at the list tail still moves above an unpicked row`() {
		assertEquals(1, target(110f, selected = listOf(3)))
	}
}
