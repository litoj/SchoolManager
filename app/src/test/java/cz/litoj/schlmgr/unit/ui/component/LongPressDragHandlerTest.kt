package cz.litoj.schlmgr.unit.ui.component

import cz.litoj.schlmgr.ui.component.ListDragMode
import cz.litoj.schlmgr.ui.component.LongPressDragHandler
import cz.litoj.schlmgr.ui.component.listDragMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a claim takes it exactly once with the mode snapshotted by the press.
 * - a reset drops it, so no stale press leaks into the next gesture.
 * - an unselected row drags a selection, a selected row moves where an order exists.
 */
class LongPressDragHandlerTest {

	@Test
	fun `a fresh handler holds no arm`() {
		val handler = LongPressDragHandler()
		assertFalse(handler.isArmed)
		assertNull(handler.claim())
	}

	@Test
	fun `a claim takes the arm exactly once`() {
		val handler = LongPressDragHandler()
		handler.arm(3, ListDragMode.Select)
		assertTrue(handler.isArmed)
		assertEquals(3 to ListDragMode.Select, handler.claim())
		assertFalse(handler.isArmed)
		assertNull(handler.claim())
	}

	@Test
	fun `a reset drops the arm`() {
		val handler = LongPressDragHandler()
		handler.arm(3, ListDragMode.Reorder)
		handler.reset()
		assertFalse(handler.isArmed)
		assertNull(handler.claim())
	}

	@Test
	fun `a claim keeps the mode the press snapshotted`() {
		val handler = LongPressDragHandler()
		handler.arm(3, ListDragMode.Reorder)
		assertEquals(3 to ListDragMode.Reorder, handler.claim())
	}

	@Test
	fun `a new arm replaces the previous row and mode`() {
		val handler = LongPressDragHandler()
		handler.arm(3, ListDragMode.Select)
		handler.arm(7, ListDragMode.Reorder)
		assertEquals(7 to ListDragMode.Reorder, handler.claim())
	}

	@Test
	fun `an unordered list drags a selection, never an order`() {
		assertEquals(ListDragMode.Select, listDragMode(reorderable = false, pressedSelected = true))
	}

	@Test
	fun `only a selected row in an ordered list moves`() {
		assertEquals(ListDragMode.Select,
			listDragMode(reorderable = true, pressedSelected = false))
		assertEquals(ListDragMode.Reorder,
			listDragMode(reorderable = true, pressedSelected = true))
	}
}
