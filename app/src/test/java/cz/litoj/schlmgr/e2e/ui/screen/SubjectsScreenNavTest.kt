package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
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
 * The explorer top bar as the way out of a transient mode.
 * - a selection and an armed move both turn the nav into the cancel arrow.
 * - the armed move reads on the top strip, its actions split the bottom strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenNavTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	private fun text(res: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(res, *args)

	private fun openChapter() {
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()
	}

	/** Long-presses the first word, which shows the selection at once. */
	private fun selectFirstWord() {
		rule.onNodeWithText("t1").performTouchInput {
			down(center)
			advanceEventTime(1_000)
			up()
		}
		rule.waitForIdle()
	}

	private fun screen(model: MainExplorerViewModel) {
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
	}

	@Test
	fun `the nav arrow clears the selection`() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		screen(model)
		openChapter()
		selectFirstWord()
		rule.runOnIdle { assertTrue(model.items().any { it.selected }) }

		rule.onNodeWithTag("top_bar_nav").performClick()
		rule.waitForIdle()

		rule.runOnIdle {
			assertFalse("the arrow cleared the selection", model.items().any { it.selected })
		}
	}

	@Test
	fun `an armed move reads at the top and the arrow cancels it`() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		screen(model)
		openChapter()
		selectFirstWord()
		rule.onNodeWithText(text(R.string.cut)).performClick()
		rule.waitForIdle()
		rule.runOnIdle { assertTrue("the cut armed a move", model.pasteArmed) }

		// the armed status takes the top line from the count
		rule.onNodeWithText(text(R.string.paste_status_move, 1)).assertExists()
		rule.onNodeWithText(text(R.string.data_children_untested, 2, "")).assertDoesNotExist()

		// the strip gives each action its half
		val cancel = rule.onNodeWithText(text(R.string.objects_cancel)).getBoundsInRoot()
		val paste = rule.onNodeWithText(text(R.string.paste)).getBoundsInRoot()
		val root = rule.onRoot().getBoundsInRoot()
		val half = (root.right - root.left) / 2
		assertTrue("cancel sits in the left half: $cancel",
			(cancel.left + cancel.right) / 2 < half)
		assertTrue("paste sits in the right half: $paste",
			(paste.left + paste.right) / 2 > half)

		// the top arrow cancels the move and the count returns
		rule.onNodeWithTag("top_bar_nav").performClick()
		rule.waitForIdle()
		rule.runOnIdle { assertFalse(model.pasteArmed) }
		rule.onNodeWithText(text(R.string.paste_status_move, 1)).assertDoesNotExist()
		rule.onNodeWithText(text(R.string.data_children_untested, 2, "")).assertExists()
	}
}
