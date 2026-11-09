package cz.litoj.schlmgr.e2e.ui.screen

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
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
	fun `a launched run shows only the first description line`() {
		awaitRun()

		rule.onNodeWithText("house info").assertExists()
		rule.onNodeWithText("more info").assertDoesNotExist()
	}

	@Test
	fun `an aborted run leaves the statistics and the sources alone`() {
		rule.waitUntil { TestRunState.hasRun() }
		TestRunState.setAnswer(0, "house")
		// other tests submit the same word, so only the change counts
		val before = ItemRepository.get(wordId)!!

		rule.onNodeWithText(activity.getString(R.string.test_abort))
			.performClick()
		rule.onNodeWithText(activity.getString(R.string.ok)).performClick()

		rule.waitForIdle()
		assertTrue(activity.isFinishing)
		assertFalse(TestRunState.hasRun())
		assertFalse(Dialogs.isShowing<TestResultsPopup>())
		assertEquals(1, TestSetupState.list.size)
		val word = ItemRepository.get(wordId)!!
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

	private lateinit var db: ItemDatabase
	private var wordId = 0

	@Before
	fun seed() {
		TestRunState.reset()
		TestSetupState.reset()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
		).allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "house", "house info\nmore info", listOf("dům" to null))
		val word = ItemRepository.children(c1.id).single()
		wordId = word.id
		val row = ItemRow(word, c1.id, 1)
		TestSetupState.addPicked(listOf(TestSetupState.seedOf(row)))
		scenario = ActivityScenario.launch(TestActivity::class.java)
			.onActivity { activity = it }
	}

	@After
	fun tearDown() {
		TestRunState.reset()
		Dialogs.clear()
		scenario.close()
		TestSetupState.reset()
		db.close()
		ItemRepository.resetForTest()
	}

	private companion object {
		const val WAIT_MS = 5_000L
	}
}
