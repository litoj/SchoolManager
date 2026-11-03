package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.RowGeometry
import cz.litoj.schlmgr.ui.component.insertIndexAt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the insertion slot counts the other rows whose middles the finger passed.
 * - the rows above the viewport always count, since their middles sit above the finger.
 */
class InsertIndexTest {

	/** Four rows of 100, at 0, 100, 200 and 300, so their middles sit at 50, 150, 250 and 350. */
	private fun rows(first: Int = 0, count: Int = 4): List<RowGeometry> =
		List(count) { RowGeometry(index = first + it, offset = it * 100, size = 100) }

	private fun insert(y: Float, anchor: Int, visible: List<RowGeometry> = rows()): Int =
		insertIndexAt(visible, anchor, y)

	@Test
	fun `a finger in the upper half of a row inserts before it`() {
		assertEquals(0, insert(140f, anchor = 0))
	}

	@Test
	fun `a finger at or past the middle of the row below inserts after it`() {
		assertEquals(1, insert(150f, anchor = 0))
		assertEquals(1, insert(160f, anchor = 0))
	}

	@Test
	fun `a finger above the dragged row inserts before the rows it passed`() {
		assertEquals(1, insert(90f, anchor = 2))
	}

	@Test
	fun `a finger over the dragged row itself keeps its slot`() {
		assertEquals(1, insert(140f, anchor = 1))
		assertEquals(1, insert(190f, anchor = 1))
	}

	@Test
	fun `a finger past several rows inserts past all of them`() {
		assertEquals(3, insert(560f, anchor = 0))
	}

	@Test
	fun `the rows above the viewport count too`() {
		// rows 0 and 1 scrolled away, the visible list starts at index 2
		assertEquals(3, insert(140f, anchor = 5, visible = rows(first = 2)))
	}

	@Test
	fun `a dragged row scrolled out above keeps its own count out`() {
		assertEquals(2, insert(140f, anchor = 1, visible = rows(first = 2)))
	}
}
