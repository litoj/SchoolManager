package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.geometry.Offset
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
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - all variants flip the row in place and never reload the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchClickVariantsTest {

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
	}

	@After
	fun tearDown() {
		// the saved position of the picker is process-wide, so leave the root for the next class
		PickerViewModel.resetSavedHistory()
		db.close()
	}

	@Test
	fun `picker - clicking a word of the results keeps the search`() {
		val model = PickerViewModel(Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()
		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
			assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
			assertTrue(model.items().isNotEmpty())
		}
	}

	@Test
	fun `closed field - clicking a word of the results keeps the search`() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.runOnIdle { model.closeSearchField() }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()
		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertEquals(SearchUi.RESULTS, model.state.value.searchUi)
			assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
			assertTrue(model.items().isNotEmpty())
		}
	}

	@Test
	fun `double click - clicking a word of the results keeps the search`() {
		// one more matching word, so a single-row flip must not satisfy the assertion
		val a = ItemRepository.subjects().single()
		val c1 = ItemRepository.children(a.id).single()
		ItemRepository.createWord(c1.id, "xy2", null, listOf("t2" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()

		// one injection holds both taps, the event times stay inside the double window
		rule.onNodeWithText("xyz").performTouchInput {
			down(Offset(20f, 20f))
			advanceEventTime(60)
			up()
			advanceEventTime(80)
			down(Offset(20f, 20f))
			advanceEventTime(60)
			up()
		}
		rule.waitForIdle()
		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
			assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
			assertEquals("both hits flipped in place — the whole word block flipped",
				listOf("t1", "t2"), model.items().map { it.toShow })
			assertTrue(model.items().all { it.flipped })
		}
	}
}
