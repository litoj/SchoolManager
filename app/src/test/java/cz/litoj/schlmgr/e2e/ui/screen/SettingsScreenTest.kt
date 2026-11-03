package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.CUT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.DELETE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.EDIT
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.GOTO
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The selection bar editor: the drag reorder and the Go-to drag-connect.
 * - the rows answer with their labels, the state carries the outcome.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var model: SettingsViewModel

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		model = SettingsViewModel()
		rule.setContent { SettingsScreen(model) {} }
		rule.waitForIdle()
	}

	private fun rowCenter(label: String): Offset {
		val node = rule.onNodeWithText(label).fetchSemanticsNode()
		return Offset(
			node.positionInWindow.x + node.size.width / 2f,
			node.positionInWindow.y + node.size.height / 2f,
		)
	}

	private fun drag(from: String, to: String) {
		// the target scrolls first, so the source lands on screen with it in reach
		rule.onNodeWithText(to).performScrollTo()
		rule.onNodeWithText(from).performScrollTo()
		rule.waitForIdle()
		val start = rowCenter(from)
		val end = rowCenter(to)
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(start)
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		// the long press fires on the main clock, so the input time alone never triggers it
		rule.mainClock.advanceTimeBy(1000)
		rule.onRoot().performTouchInput {
			moveTo(Offset(end.x, end.y - 1f))
			moveTo(end)
		}
		rule.waitForIdle()
		rule.onRoot().performTouchInput { up() }
		rule.waitForIdle()
		rule.mainClock.autoAdvance = true
	}

	@Test
	fun `dragging Go-to onto a strip row connects it`() {
		drag("Go to location", "Edit")

		assertEquals(EDIT, model.state.connections[GOTO])
	}

	@Test
	fun `dropping Go-to off the strip releases it`() {
		drag("Go to location", "Export")

		assertNull(model.state.connections[GOTO])
	}

	@Test
	fun `a long-press drag reorders the strip`() {
		// the Test action row answers with its description, since the Test section
		// header carries the same label
		drag("Delete", "Starts a test from the selected rows")

		assertEquals(DELETE, model.state.selectionOrder.first())
		assertEquals(CUT, model.state.connections[GOTO])
	}

	@Test
	fun `a reorder that pushes the target off the strip clears the connection`() {
		drag("Cut", "References")

		assertNull(model.state.connections[GOTO])
	}
}
