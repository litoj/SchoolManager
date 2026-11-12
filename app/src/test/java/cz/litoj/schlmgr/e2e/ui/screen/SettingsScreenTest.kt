package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.DELETE
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction.GOTO
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The selection action editor: the drag reorder and the placement markers.
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
		// the engine survives between tests, so the direction pin starts from the word
		TestEngine.applyReversed(false)
		TestEngine.applyIgnoredMistakes("")
		AppSettings.set("reverseTest", false)
		AppSettings.set("testIgnoredMistakes", "")
		model = SettingsViewModel()
		rule.setContent { SettingsScreen(model) {} }
		rule.waitForIdle()
	}

	private fun ignoredField(index: Int) = rule.onNodeWithTag("ignored_mistake_$index").onChild()

	private fun rowCenter(tag: String): Offset {
		val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
		return Offset(
			node.positionInWindow.x + node.size.width / 2f,
			node.positionInWindow.y + node.size.height / 2f,
		)
	}

	private fun drag(from: String, to: String) {
		// the target scrolls first, so the source lands on screen with it in reach
		rule.onNodeWithTag(to).performScrollTo()
		rule.onNodeWithTag(from).performScrollTo()
		rule.waitForIdle()
		val start = rowCenter(from)
		val target = rowCenter(to)
		// the insertion follows the half of the row the finger sits in,
		// so aim past the middle toward the drag direction
		val end = Offset(target.x, target.y + if (target.y < start.y) -12f else 12f)
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
	fun `dragging Go-to to the front puts it on the search bar`() {
		drag("action_GOTO", "action_TEST")

		assertEquals(GOTO, model.state.selectionOrder.first())
		assertEquals(GOTO, ActionConfig(model.state.selectionOrder).bar(true, direct = 4).first())
	}

	@Test
	fun `the default direction flips and persists`() {
		val label = RuntimeEnvironment.getApplication().getString(R.string.setts_test_direction)
		val row = rule.onNodeWithText(label)
		row.performScrollTo()
		rule.waitForIdle()
		assertFalse("the word is typed by default, a translation shows", TestEngine.isReversed)

		row.performClick()
		rule.waitForIdle()

		assertTrue("the default flipped to the typed translation", TestEngine.isReversed)
		assertTrue("the default reached the storage", AppSettings.getBool("reverseTest", false))
	}

	@Test
	fun `an ignored text field always leaves one empty field`() {
		ignoredField(0).performScrollTo().performTextInput("。")
		rule.waitForIdle()
		ignoredField(0).performImeAction()
		rule.waitForIdle()
		ignoredField(1).assertIsFocused()
		assertEquals("。", model.state.ignoredMistakesText)

		ignoredField(1).performTextInput("...")
		rule.waitForIdle()

		ignoredField(2).assertExists()
		assertEquals("。\n...", model.state.ignoredMistakesText)

		ignoredField(0).performTextClearance()
		rule.waitForIdle()

		ignoredField(1).assertExists()
		rule.onNodeWithTag("ignored_mistake_2").assertDoesNotExist()
		assertEquals("...", model.state.ignoredMistakesText)
	}

	@Test
	fun `a long-press drag reorders the actions`() {
		drag("action_DELETE", "action_TEST")

		assertEquals(DELETE, model.state.selectionOrder.first())
	}

	@Test
	fun `a second drag starts under the finger`() {
		// the first drag moves Export up, so the next grab finds it in another slot
		drag("action_EXPORT", "action_CUT")
		val tag = "action_EXPORT"
		rule.onNodeWithTag(tag).performScrollTo()
		rule.waitForIdle()
		val bounds = rule.onNodeWithTag(tag).getBoundsInRoot()
		val x = with(rule.density) { (bounds.left + (bounds.right - bounds.left) / 2).toPx() }
		val y = with(rule.density) { (bounds.top + (bounds.bottom - bounds.top) / 2).toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(Offset(x, y))
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		rule.mainClock.advanceTimeBy(1000)
		rule.onRoot().performTouchInput { moveTo(Offset(x, y - 5f)) }
		rule.waitForIdle()
		val dragged = rule.onNodeWithTag(tag).getBoundsInRoot()
		val finger = with(rule.density) { (y - 5f).toDp() }
		assertTrue(
			"the row must stay under the finger, not jump to its old slot: dragged=$dragged finger=$finger",
			finger >= dragged.top && finger <= dragged.bottom,
		)
		rule.onRoot().performTouchInput { up() }
		rule.mainClock.autoAdvance = true
	}

	@Test
	fun `a reorder moves cut out of the browse bar`() {
		drag("action_CUT", "action_EXPORT")

		assertFalse(ActionConfig(model.state.selectionOrder).bar(false, direct = 4).contains(
			cz.litoj.schlmgr.ui.viewmodel.SelectionAction.CUT))
	}
}
