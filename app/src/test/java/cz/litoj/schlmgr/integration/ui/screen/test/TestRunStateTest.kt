package cz.litoj.schlmgr.integration.ui.screen.test

import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the end of the run closes a question that asked about the answers.
 * - no live run holds nothing to ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestRunStateTest {

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
