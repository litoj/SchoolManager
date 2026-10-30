package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the New button survives a gesture where the list fits the viewport.
 * - a covering gesture over scrollable content drops it.
 * - a chapter change brings it back.
 * - a delete keeps it down, only a load brings it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenFabTest {

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
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun `the button keeps on a short list, drops on a gesture, returns on navigation`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		repeat(30) { i ->
			ItemRepository.createWord(c1.id, "w$i", null, listOf("t$i" to null))
		}
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()

		// the root holds one subject, the list fits the viewport
		rule.onNodeWithContentDescription("New").assertIsDisplayed()
		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertIsDisplayed()

		// the chapter holds thirty words, the covering gesture drops the button
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()
		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertDoesNotExist()

		// the way back to the subject resets the button
		model.onBackRequested()
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertIsDisplayed()
	}

	@Test
	fun `a delete keeps the button down, only a load brings it back`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		repeat(30) { i ->
			ItemRepository.createWord(c1.id, "w$i", null, listOf("t$i" to null))
		}
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()

		// the selection starts while the first row is in view.
		// a word shows its translation by default, so the row reads t0
		rule.onNodeWithText("t0").performTouchInput { longClick() }
		rule.waitForIdle()
		// the swipe hides the button and drags the selection along
		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertDoesNotExist()

		// the selection delete changes no load, so the button stays down.
		// the confirm popup needs the app shell, the commit runs through the model
		rule.runOnIdle {
			model.performDelete(model.items().filter { it.selected })
		}
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertDoesNotExist()

		// the way back is a load, it resets the button
		model.onBackRequested()
		rule.waitForIdle()
		rule.onNodeWithContentDescription("New").assertIsDisplayed()
	}
}
