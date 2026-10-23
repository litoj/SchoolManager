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
 * - a search-result list always drags a selection, never an order.
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
	fun `a search-result list drags a selection, never an order`() {
		assertEquals(
			ListDragMode.Select,
			listDragMode(pickerMode = false, selectionActive = false, searchList = true, reorderable = false),
		)
		// a reorderable container behind the results changes nothing, the list is a result list
		assertEquals(
			ListDragMode.Select,
			listDragMode(pickerMode = false, selectionActive = false, searchList = true, reorderable = true),
		)
	}

	@Test
	fun `the drag follows the selection, the picker and the reorder flag`() {
		assertEquals(ListDragMode.Select,
			listDragMode(pickerMode = true, selectionActive = false, searchList = false, reorderable = true))
		assertEquals(ListDragMode.Select,
			listDragMode(pickerMode = false, selectionActive = true, searchList = false, reorderable = true))
		assertEquals(ListDragMode.Reorder,
			listDragMode(pickerMode = false, selectionActive = false, searchList = false, reorderable = true))
		assertEquals(ListDragMode.None,
			listDragMode(pickerMode = false, selectionActive = false, searchList = false, reorderable = false))
	}
}
