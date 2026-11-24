package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.TestResultsHelpPopup
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
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
import org.robolectric.annotation.Config

/**
 * - the title help icon opens the results help above the report.
 * - each caption is followed by report rows and marks, and OK closes only the help.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestResultsHelpTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
		).allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
	}

	private fun results(): TestResultsPopup {
		val subject = ItemRepository.createSubject("A", null)
		val chapter = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(chapter.id, "chleba", null, listOf("bread" to null))
		val model = TestItemModel(TestEntry(word, listOf(word), ItemRepository.translations(word.id)))
		model.answer = "chleba"
		return TestResultsPopup(listOf(model), 100f, false)
	}

	private fun text(res: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(res, *args)

	@Test
	fun `the title help icon opens the results help`() {
		val popup = results()
		rule.setContent { popup.Content() }

		rule.onNodeWithContentDescription(text(R.string.help)).performClick()

		rule.runOnIdle {
			assertTrue(Dialogs.isShowing<TestResultsPopup>())
			assertTrue(Dialogs.isShowing<TestResultsHelpPopup>())
		}
	}

	@Test
	fun `the help shows the report rows and marks and OK closes only the help`() {
		results()
		rule.setContent {
			// the test renders the stack itself, since no dialog host runs here
			val stack by Dialogs.stack.collectAsState()
			stack.forEach { it.Content() }
		}

		rule.onNodeWithContentDescription(text(R.string.help)).performClick()

		// the caption rows are report rows, so their counts are the report's own
		rule.onNodeWithText(text(R.string.test_results_help_low)).assertExists()
		rule.onNodeWithText(text(R.string.test_results_help_high)).assertExists()
		rule.onNodeWithText("0/9").assertExists()
		rule.onNodeWithText("9/9").assertExists()
		// the sample shows the merged answer diff, so the typed text gains the missing letter
		rule.onNodeWithText("Your answer: alwayzs right").assertExists()
		rule.onNodeWithText("Correct: always right").assertExists()

		// the report and the help each carry one OK, and the help sits on top
		val ok = rule.onAllNodesWithText(text(R.string.ok))
		assertEquals("both popups show their own OK", 2, ok.fetchSemanticsNodes().size)
		ok[1].performClick()

		rule.runOnIdle {
			assertFalse(Dialogs.isShowing<TestResultsHelpPopup>())
			assertTrue(Dialogs.isShowing<TestResultsPopup>())
		}
	}
}
