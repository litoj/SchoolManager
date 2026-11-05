package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.CUT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.DELETE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.EDIT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.EXPORT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.FLIP
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.GOTO
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.REFERENCE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.TEST
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The stored order, where an old record upgrades and a damaged one falls back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionConfigLoadTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		AppSettings.remove(ORDER_KEY)
	}

	@After
	fun tearDown() {
		AppSettings.remove(ORDER_KEY)
	}

	@Test
	fun `a legacy order gains goto after cut`() {
		AppSettings.set(ORDER_KEY, legacy.joinToString(",") { it.name })

		assertEquals(
			listOf(TEST, CUT, GOTO, EDIT, DELETE, REFERENCE, FLIP, EXPORT),
			ActionConfig.load().order,
		)
	}

	@Test
	fun `a custom legacy order keeps its pick order`() {
		val custom = listOf(EDIT, TEST, CUT, DELETE, REFERENCE, FLIP, EXPORT)
		AppSettings.set(ORDER_KEY, custom.joinToString(",") { it.name })

		assertEquals(
			listOf(EDIT, TEST, CUT, GOTO, DELETE, REFERENCE, FLIP, EXPORT),
			ActionConfig.load().order,
		)
	}

	@Test
	fun `a damaged record falls back to the default`() {
		AppSettings.set(ORDER_KEY, "TEST,CUT,UNKNOWN")

		assertEquals(ActionConfig.DEFAULT_ORDER, ActionConfig.load().order)
	}

	private val legacy = listOf(TEST, CUT, EDIT, DELETE, REFERENCE, FLIP, EXPORT)

	private companion object {
		const val ORDER_KEY = "selection_actions"
	}
}
