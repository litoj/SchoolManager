package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.RowGeometry
import cz.litoj.schlmgr.ui.component.reorderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * - the swap needs the finger past the middle of the row under it.
 * - the flicker case holds one finger y across both layouts of a swap.
 */
class ReorderTargetTest {

	private val viewportHeight = 1000f
	private val zone = 100f

	/** A short dragged row above a tall one, the flicker shape. */
	private val before = listOf(
		RowGeometry(index = 0, offset = 0, size = 100),
		RowGeometry(index = 1, offset = 100, size = 400),
		RowGeometry(index = 2, offset = 500, size = 100),
	)

	/** The same rows after the dragged short row swapped below the tall one. */
	private val after = listOf(
		RowGeometry(index = 0, offset = 0, size = 400),
		RowGeometry(index = 1, offset = 400, size = 100),
		RowGeometry(index = 2, offset = 500, size = 100),
	)

	private fun target(y: Float, dragIndex: Int, visible: List<RowGeometry> = before): Int? =
		reorderTarget(visible, dragIndex, y, viewportHeight, zone)

	@Test
	fun `a finger short of the middle of the row below swaps nothing`() {
		assertNull(target(150f, dragIndex = 0))
	}

	@Test
	fun `a finger at or past the middle of the row below swaps down`() {
		assertEquals(1, target(300f, dragIndex = 0))
		assertEquals(1, target(350f, dragIndex = 0))
	}

	@Test
	fun `the finger that swapped down holds its place after the swap`() {
		// the swap moved the tall row up, so its middle sits at 200
		assertNull(target(300f, dragIndex = 1, visible = after))
	}

	@Test
	fun `a finger in the top half of the row above swaps up`() {
		assertEquals(0, target(150f, dragIndex = 1, visible = after))
	}

	@Test
	fun `a finger in the bottom half of the row above swaps nothing`() {
		assertNull(target(250f, dragIndex = 1, visible = after))
	}

	@Test
	fun `a finger over the dragged row itself swaps nothing`() {
		assertNull(target(150f, dragIndex = 1))
		assertNull(target(450f, dragIndex = 1))
	}

	@Test
	fun `a finger past the middle of a far row jumps to it`() {
		assertEquals(2, target(560f, dragIndex = 0))
	}

	@Test
	fun `equal rows swap at and past the half`() {
		val equal = listOf(
			RowGeometry(index = 0, offset = 0, size = 100),
			RowGeometry(index = 1, offset = 100, size = 100),
			RowGeometry(index = 2, offset = 200, size = 100),
		)
		assertNull(target(140f, dragIndex = 0, visible = equal))
		assertEquals(1, target(150f, dragIndex = 0, visible = equal))
		assertEquals(1, target(160f, dragIndex = 0, visible = equal))
	}

	@Test
	fun `a finger past the viewport edges keeps the clamped row`() {
		assertEquals(0, target(-10f, dragIndex = 1))
		assertEquals(2, target(950f, dragIndex = 0))
	}
}
