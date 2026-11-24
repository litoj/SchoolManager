package cz.litoj.schlmgr.e2e.ui.screen

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.testing.TestRunData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - a seeded run shows the picked word, the whole main path of the screen.
 * - the abort button drops the run with no trace in the statistics.
 * - Done asks only about blank answers, so a timed end never waits for a tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestActivityTest {

	private val testSetup get() = TestSetupState.shared

	@get:Rule
	val rule = createComposeRule()

	private lateinit var scenario: ActivityScenario<TestActivity>
	private lateinit var activity: TestActivity

	// the run lands through a posted message, which the compose wait alone never drains
	private fun await(landed: () -> Boolean) = rule.waitUntil(WAIT_MS) {
		shadowOf(Looper.getMainLooper()).idle()
		landed()
	}

	private fun awaitRun() = await { TestRunState.items.isNotEmpty() }

	private fun awaitResults() = await { Dialogs.isShowing<TestResultsPopup>() }

	@Test
	fun `a launched run shows the picked word`() {
		awaitRun()
		assertTrue(TestRunState.items.isNotEmpty())
		// the translations prompt by default, see the screen
		rule.onNodeWithText("dům").assertExists()
	}

	@Test
	fun `a launched run shows the description lines the setting allows`() {
		awaitRun()

		// the default setting allows five lines, so the note shows whole
		rule.onNodeWithText("dům info\nmore info").assertExists()
	}

	@Test
	fun `an aborted run leaves the statistics and the sources alone`() {
		rule.waitUntil { TestRunState.hasRun() }
		TestRunState.setAnswer(0, "house")
		// other tests submit the same word, so only the change counts
		val before = freshWord()!!

		rule.onNodeWithText(activity.getString(R.string.test_abort))
			.performClick()
		rule.onNodeWithText(activity.getString(R.string.ok)).performClick()

		rule.waitForIdle()
		assertTrue(activity.isFinishing)
		assertFalse(TestRunState.hasRun())
		assertFalse(Dialogs.isShowing<TestResultsPopup>())
		assertEquals(1, testSetup.list.size)
		val word = freshWord()!!
		assertEquals(before.passedTests, word.passedTests)
		assertEquals(before.failedTests, word.failedTests)
	}

	@Test
	fun `done asks with the count while an answer is blank`() {
		awaitRun()
		TestRunState.setAnswer(0, "  ")

		rule.onNodeWithText("Done").performClick()

		assertTrue(Dialogs.isShowing<ContinuePopup>())
		assertTrue(TestRunState.hasRun())

		rule.onNodeWithText("OK").performClick()

		awaitResults()
	}

	@Test
	fun `done submits directly with every answer filled`() {
		awaitRun()
		TestRunState.setAnswer(0, "house")

		rule.onNodeWithText("Done").performClick()

		assertFalse(Dialogs.isShowing<ContinuePopup>())
		awaitResults()
	}

	@Test
	fun `the timed submit never asks about blank answers`() {
		awaitRun()

		rule.runOnUiThread { TestRunState.submit(activity) }

		assertFalse(Dialogs.isShowing<ContinuePopup>())
		awaitResults()
	}

	private var wordId = 0

	@Before
	fun seed() {
		TestRunState.reset()
		testSetup.reset()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
		val dependencies = Startup.init(RuntimeEnvironment.getApplication())
		runBlocking(Dispatchers.IO) {
			val a = dependencies.itemEditor.createSubject("A", null)
			val c1 = dependencies.itemEditor.createChapter(a.id, "c1", null)
			val word = dependencies.itemEditor.createWord(
				c1.id, "house", "house info", listOf("dům" to "dům info\nmore info"))
			wordId = word.id
			val row = ItemRow(word, c1.id, 1)
			val chain = listOf(a, c1, word)
			val entries = mapOf(chain to TestRunData.readEntries(listOf(chain), dependencies.graphQueries))
			testSetup.addPicked(
				listOf(testSetup.seedOf(row)), mapOf(c1.id to listOf(a, c1)), entries)
		}
		scenario = ActivityScenario.launch(TestActivity::class.java)
			.onActivity { activity = it }
	}

	@After
	fun tearDown() {
		TestRunState.reset()
		Dialogs.clear()
		scenario.close()
		testSetup.reset()
	}

	private fun freshWord() = runBlocking(Dispatchers.IO) {
		Startup.dependencies.graphQueries.get(wordId)
	}

	private companion object {
		const val WAIT_MS = 5_000L
	}
}
