package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
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
 * The type icon of a row selects it while a selection runs or the picker shows.
 * - the row tap keeps its own meaning, the word flip in the picker included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconSelectTest {

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
		// the saved position of the picker is process-wide, so leave the root for the next class
		PickerViewModel.resetSavedHistory()
		db.close()
	}

	@Test
	fun `picker - the icon selects the row, the row tap still flips the word`() {
		val model = PickerViewModel(Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()

		// the default flip shows translations, so the row reads its translation
		rule.onNodeWithText("t1").performClick()
		rule.waitForIdle()
		// the unflipped row shows the word, so the icon carries its name
		rule.onNodeWithContentDescription("xyz").performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertFalse("the row tap unflipped the word",
				model.items().single { it.item.name == "xyz" }.flipped)
			assertTrue("the icon selected the word",
				model.items().single { it.item.name == "xyz" }.selected)
		}
	}

	@Test
	fun `selection - the icon selects another row of the running selection`() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()
		// a long-press starts the selection, the icon then adds the second word
		rule.onNodeWithText("t1").performTouchInput {
			down(center)
			advanceEventTime(1_000)
			up()
		}
		rule.waitForIdle()
		// the default flip shows translations, so the icon carries the display name
		rule.onNodeWithContentDescription("t2").performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertTrue("the icon added the second word to the selection",
				model.items().single { it.item.name == "w2" }.selected)
			assertTrue("the first selection stays",
				model.items().single { it.item.name == "xyz" }.selected)
			// the default flip stays, the icon never flips a word
			assertTrue("the icon left the second word flipped",
				model.items().single { it.item.name == "w2" }.flipped)
		}
	}
}
