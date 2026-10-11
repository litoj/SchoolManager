package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.RowGeometry
import cz.litoj.schlmgr.ui.component.indexAtY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure finger-to-row mapping ([indexAtY]).
 * - the finger's row, the first row above the viewport, the last row only at
 *   the bottom edge.
 * - nothing for a gap or under a short list.
 * - three rows at non-zero indices prove the mapping returns the row's own
 *   index, not its position in the visible window.
 * - select auto-scroll is not covered — it lives in the gesture loop
 *   ([rememberListDragModifier] steps the selection while scrolling), so
 *   it needs a composed list.
 */
class IndexAtYTest {

	private val viewportHeight = 1000f
	private val zone = 100f

	/** Three 100px rows at the top of a viewport with room to spare under them. */
	private val rows = listOf(
		RowGeometry(index = 5, offset = 0, size = 100),
		RowGeometry(index = 6, offset = 100, size = 100),
		RowGeometry(index = 7, offset = 200, size = 100),
	)

	private fun index(y: Float, visible: List<RowGeometry> = rows): Int? =
		indexAtY(visible, y, viewportHeight, zone)

	@Test
	fun `a finger inside a row maps to that row`() {
		assertEquals(5, index(0f))
		assertEquals(5, index(50f))
		assertEquals(6, index(150f))
		assertEquals(7, index(299f))
	}

	@Test
	fun `a finger above every row maps to the first row`() {
		assertEquals(5, index(-1f))
		assertEquals(5, index(-500f))
	}

	/** Once scrolled the first offset goes negative, so only the viewport top still finds it. */
	@Test
	fun `a scrolled list maps a finger above the viewport to the first row`() {
		val scrolled = listOf(
			RowGeometry(index = 5, offset = -30, size = 20),
			RowGeometry(index = 6, offset = 100, size = 100),
			RowGeometry(index = 7, offset = 200, size = 100),
		)
		assertEquals(5, index(-5f, scrolled))
		assertEquals(5, index(-500f, scrolled))
		assertEquals(6, index(150f, scrolled))
	}

	@Test
	fun `a finger held at the bottom edge maps to the last row`() {
		assertEquals(7, index(viewportHeight - zone + 1f))
		assertEquals(7, index(viewportHeight))
		assertEquals(7, index(5000f))
	}

	@Test
	fun `the empty space under a short list maps to no row`() {
		assertNull(index(300f))
		assertNull(index(500f))
		assertNull(index(viewportHeight - zone))
	}

	@Test
	fun `a finger in a gap between rows maps to nothing`() {
		val gapped = listOf(
			RowGeometry(index = 0, offset = 0, size = 80),
			RowGeometry(index = 1, offset = 100, size = 80),
		)
		assertNull(index(90f, gapped))
	}

	@Test
	fun `an empty list maps to nothing`() {
		assertNull(indexAtY(emptyList(), 50f, viewportHeight, zone))
	}
}
