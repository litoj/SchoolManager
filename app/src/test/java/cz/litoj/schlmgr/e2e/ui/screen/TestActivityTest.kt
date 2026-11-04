package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - a seeded run shows the picked word, the whole main path of the screen.
 * - the abort button drops the run with no trace in the statistics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestActivityTest {

	@get:Rule
	val rule = createAndroidComposeRule<TestActivity>()

	@Test
	fun `a launched run shows the picked word`() {
		rule.waitForIdle()
		assertTrue(TestRunState.items.isNotEmpty())
		// the translations prompt by default, see the screen
		rule.onNodeWithText("dům").assertExists()
	}

	@Test
	fun `an aborted run leaves the statistics and the sources alone`() {
		rule.waitUntil { TestRunState.hasRun() }
		TestRunState.setAnswer(0, "house")
		// other tests submit the same word, so only the change counts
		val before = ItemRepository.get(wordId)!!

		rule.onNodeWithText(rule.activity.getString(R.string.test_abort))
			.performClick()
		rule.onNodeWithText(rule.activity.getString(R.string.ok)).performClick()

		rule.waitForIdle()
		assertTrue(rule.activity.isFinishing)
		assertFalse(TestRunState.hasRun())
		assertFalse(Dialogs.isShowing<TestResultsPopup>())
		assertEquals(1, TestSetupState.list.size)
		val word = ItemRepository.get(wordId)!!
		assertEquals(before.passedTests, word.passedTests)
		assertEquals(before.failedTests, word.failedTests)
	}

	companion object {

		private lateinit var db: ItemDatabase
		private var wordId = 0

		@JvmStatic
		@BeforeClass
		fun seed() {
			// the class setup runs before the activity launches, so the run finds its data
			GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
			db = Room.inMemoryDatabaseBuilder(
				RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
			).allowMainThreadQueries().build()
			ItemRepository.initForTest(db)
			ItemRepository.ensureRoot()
			val a = ItemRepository.createSubject("A", null)
			val c1 = ItemRepository.createChapter(a.id, "c1", null)
			ItemRepository.createWord(c1.id, "house", null, listOf("dům" to null))
			val word = ItemRepository.children(c1.id).single()
			wordId = word.id
			val row = ItemRow(word, c1.id, 1)
			TestSetupState.addPicked(listOf(TestSetupState.seedOf(row)))
		}

		@JvmStatic
		@AfterClass
		fun clean() {
			TestRunState.reset()
			db.close()
			ItemRepository.resetForTest()
		}
	}
}
