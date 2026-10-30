package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
		ItemRepository.resetForTest()
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
		ItemRepository.createWord(c1.id, "xy3", null, listOf("t3" to null))
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
			assertEquals("one flipped row against the plain pair, the pair flips over",
				listOf("t1", "t2", "t3"), model.items().map { it.toShow })
			assertTrue(model.items().all { it.flipped })
		}
	}

	@Test
	fun `double click - the double flips the results to the other side of the majority`() {
		// one more matching word, so a single-row flip must not satisfy the assertion
		val a = ItemRepository.subjects().single()
		val c1 = ItemRepository.children(a.id).single()
		ItemRepository.createWord(c1.id, "xy2", null, listOf("t2" to null))
		ItemRepository.createWord(c1.id, "xy3", null, listOf("t3" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()

		// two single taps flip two rows, the places sit apart so no double forms
		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("xy2").performClick()
		rule.waitForIdle()

		// one injection holds both taps, the event times stay inside the double window
		rule.onNodeWithText("xy3").performTouchInput {
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
			assertEquals("the flipped majority flips back to the word side",
				listOf("xyz", "xy2", "xy3"), model.items().map { it.toShow })
			assertTrue(model.items().none { it.flipped })
		}
	}

	@Test
	fun `double click - the empty part double flips the results to the other side of the majority`() {
		// one more matching word, so a single-row flip must not satisfy the assertion
		val a = ItemRepository.subjects().single()
		val c1 = ItemRepository.children(a.id).single()
		ItemRepository.createWord(c1.id, "xy2", null, listOf("t2" to null))
		ItemRepository.createWord(c1.id, "xy3", null, listOf("t3" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()

		// two single taps flip two rows, the places sit apart so no double forms
		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("xy2").performClick()
		rule.waitForIdle()

		// both taps land on the empty part below the rows, no row sits under them
		val last = rule.onNodeWithText("xy3").getUnclippedBoundsInRoot()
		val below = with(rule.density) {
			Offset((last.left + 50.dp).toPx(), (last.bottom + 50.dp).toPx())
		}
		rule.onRoot().performTouchInput {
			down(below)
			advanceEventTime(60)
			up()
			advanceEventTime(80)
			down(below)
			advanceEventTime(60)
			up()
		}
		rule.waitForIdle()
		rule.runOnIdle {
			assertEquals("the flipped majority flips back to the word side",
				listOf("xyz", "xy2", "xy3"), model.items().map { it.toShow })
			assertTrue(model.items().none { it.flipped })
		}
	}

	@Test
	fun `double click - the flip of the first tap moves the rows, the place holds`() {
		// one more matching word, so a single-row flip must not satisfy the assertion
		val a = ItemRepository.subjects().single()
		val c1 = ItemRepository.children(a.id).single()
		// the tall description makes the plain row tall, the flip to one
		// translation line shrinks it and pulls the row below over the tap
		ItemRepository.createWord(c1.id, "xyz", "a description\nover three\nlines", listOf("t1" to null))
		ItemRepository.createWord(c1.id, "xy2", null, listOf("t2" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()

		// both taps land at the bottom of the tall row, under the name line
		val tall = rule.onNodeWithText("a description", substring = true).getUnclippedBoundsInRoot()
		val low = with(rule.density) {
			Offset((tall.left + 50.dp).toPx(), (tall.bottom - 5.dp).toPx())
		}
		rule.onRoot().performTouchInput {
			down(low)
			advanceEventTime(60)
			up()
			advanceEventTime(80)
			down(low)
			advanceEventTime(60)
			up()
		}
		rule.waitForIdle()
		rule.runOnIdle {
			assertEquals("the double closed over the moved rows, a tie keeps the word side",
				listOf("xyz", "xy2"), model.items().map { it.toShow })
		}
	}
}
