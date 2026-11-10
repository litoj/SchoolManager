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
import org.junit.Test

class ActionConfigTest {

	private val default = ActionConfig(ActionConfig.DEFAULT_ORDER)

	@Test
	fun `a moved action changes the bars of its list kind`() {
		val moved = default.moved(from = 2, to = 0)

		assertEquals(listOf(GOTO, TEST, EDIT, DELETE), moved.bar(true, direct = 4))
		assertEquals(listOf(TEST, CUT, EDIT, DELETE), moved.bar(false, direct = 4))
	}

	@Test
	fun `a moved goto leaves the search bar`() {
		val moved = default.moved(from = 2, to = 6)

		// the search bar takes the first four applicable, so Goto leaves it here
		assertEquals(listOf(TEST, EDIT, DELETE, REFERENCE), moved.bar(true, direct = 4))
		assertEquals(listOf(FLIP, GOTO, EXPORT), moved.overflow(true, direct = 4))
	}
}
