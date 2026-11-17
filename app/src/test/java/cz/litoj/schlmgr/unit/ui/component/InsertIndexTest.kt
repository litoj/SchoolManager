package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.RowGeometry
import cz.litoj.schlmgr.ui.component.insertIndexAt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the insertion slot counts the unselected rows whose middles the finger passed.
 * - the rows above the viewport count too, except every selected row.
 */
class InsertIndexTest {

	/** Four rows of 100, at 0, 100, 200 and 300, so their middles sit at 50, 150, 250 and 350. */
	private fun rows(first: Int = 0, count: Int = 4): List<RowGeometry> =
		List(count) { RowGeometry(index = first + it, offset = it * 100, size = 100) }

	private fun insert(
		y: Float,
		selected: List<Int> = listOf(0),
		visible: List<RowGeometry> = rows(),
	): Int = insertIndexAt(visible, selected, y)

	@Test
	fun `a finger in the upper half of a row inserts before it`() {
		assertEquals(0, insert(140f))
	}

	@Test
	fun `a finger at or past the middle of a row inserts after it`() {
		assertEquals(1, insert(150f))
		assertEquals(1, insert(160f))
	}

	@Test
	fun `a finger over a moving row counts only the remaining rows`() {
		assertEquals(1, insert(140f, selected = listOf(1)))
		assertEquals(1, insert(190f, selected = listOf(1)))
	}

	@Test
	fun `a finger past several rows inserts past all of them`() {
		assertEquals(3, insert(560f))
	}

	@Test
	fun `the rows above the viewport count too`() {
		// rows 0 and 1 scrolled away, the visible list starts at index 2
		assertEquals(3, insert(140f, selected = listOf(5), visible = rows(first = 2)))
	}

	@Test
	fun `a non-contiguous selection counts only the remaining rows`() {
		// rows 1 and 3 move together, so neither adds a slot of its own
		assertEquals(0, insert(40f, selected = listOf(1, 3)))
		assertEquals(2, insert(360f, selected = listOf(1, 3)))
	}

	@Test
	fun `a selected row scrolled out above stays out of the count`() {
		assertEquals(2, insert(140f, selected = listOf(1), visible = rows(first = 2)))
	}
}
