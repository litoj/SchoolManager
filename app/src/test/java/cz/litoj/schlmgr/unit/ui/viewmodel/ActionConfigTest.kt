package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.DELETE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.EDIT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.EXPORT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.FLIP
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.GOTO
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.CUT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.REFERENCE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.TEST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionConfigTest {

	private val default = ActionConfig(ActionConfig.DEFAULT_ORDER, ActionConfig.DEFAULT_CONNECTIONS)

	@Test
	fun `the default bar holds four slots`() {
		assertEquals(listOf(TEST, CUT, EDIT, DELETE), default.bar(false))
	}

	@Test
	fun `a search bar substitutes the bound goto and drops cut`() {
		assertEquals(listOf(TEST, GOTO, EDIT, DELETE), default.bar(true))
	}

	@Test
	fun `the default overflow holds the unslotted actions`() {
		assertEquals(listOf(REFERENCE, FLIP, EXPORT), default.overflow(false))
	}

	@Test
	fun `an unbound goto joins only the search overflow`() {
		val unbound = ActionConfig(ActionConfig.DEFAULT_ORDER, emptyMap())

		assertEquals(listOf(TEST, EDIT, DELETE), unbound.bar(true))
		assertEquals(listOf(REFERENCE, FLIP, EXPORT, GOTO), unbound.overflow(true))
		assertEquals(listOf(REFERENCE, FLIP, EXPORT), unbound.overflow(false))
	}

	@Test
	fun `a goto bound to a strip action displaces it into the search menu`() {
		val bound = ActionConfig(ActionConfig.DEFAULT_ORDER, mapOf(GOTO to EDIT))

		assertEquals(listOf(TEST, GOTO, DELETE), bound.bar(true))
		// the displaced action keeps its picked position in the menu order
		assertEquals(listOf(EDIT, REFERENCE, FLIP, EXPORT), bound.overflow(true))
	}

	@Test
	fun `a moved action carries its order and drops a lost connection`() {
		val moved = default.moved(from = 1, to = 4)

		assertEquals(listOf(TEST, EDIT, DELETE, REFERENCE, CUT, FLIP, EXPORT), moved.order)
		assertNull(moved.connections[GOTO])
	}

	@Test
	fun `a move that keeps the target on the strip keeps the connection`() {
		val moved = default.moved(from = 3, to = 0)

		assertEquals(listOf(DELETE, TEST, CUT, EDIT), moved.order.take(4))
		assertEquals(CUT, moved.connections[GOTO])
	}
}
