package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the answer reaches the screen alive at that moment, not the one that asked.
 * - the end of the run closes the question, so it never lands above the results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestRunConfirmTest {

	@get:Rule
	val rule = createComposeRule()

	private class FakeHost : TestRunState.Host {
		override val scope = CoroutineScope(Dispatchers.Unconfined)
		override fun isAlive() = true
		override fun onNoObjects() {}
		override fun onTimeUp() {}
		override fun onResults(items: List<TestItemModel>, ratio: Float) {}
		override fun onAborted() {}
	}

	@After
	fun tearDown() {
		TestRunState.reset()
		Dialogs.clear()
	}

	@Test
	fun `ok acts on the screen a rotation attached`() {
		val rotated = FakeHost()
		var acted: TestRunState.Host? = null
		TestRunState.attach(FakeHost())
		TestRunState.confirm("Sure?") { acted = it }
		TestRunState.attach(rotated)
		val popup = Dialogs.stack.value.single()
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Sure?").assertExists()
		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertSame(rotated, acted) }
	}

	@Test
	fun `a timed submit closes the open question`() {
		val host = FakeHost()
		var acted = 0
		TestRunState.attach(host)
		TestRunState.confirm("Sure?") { acted++ }
		assertTrue(Dialogs.isShowing<ContinuePopup>())

		TestRunState.submit(host)

		assertFalse(Dialogs.isShowing<ContinuePopup>())
		assertEquals(0, acted)
	}

	@Test
	fun `an exit closes the open question`() {
		TestRunState.attach(FakeHost())
		TestRunState.confirm("Sure?") {}

		TestRunState.reset()

		assertFalse(Dialogs.isShowing<ContinuePopup>())
	}

	@Test
	fun `no live run offers nothing to abort`() {
		TestRunState.attach(FakeHost())

		TestRunState.abort("Sure?")

		assertFalse(Dialogs.isShowing<ContinuePopup>())
	}
}
