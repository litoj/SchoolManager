package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenSearchClickTest {

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
		ItemRepository.resetForTest()
	}

	@Test
	fun `toggling a word of the results keeps the search`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent {
			SubjectsScreen(model, {}, {}, {})
		}
		rule.waitForIdle()

		// into the subject, the way the search runs against open content
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()

		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("xy") }
		rule.waitForIdle()
		rule.onNodeWithText("xyz").assertExists()

		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()

		rule.onNodeWithText("t1").assertExists()
		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
			assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
			assertEquals("xy", model.query.value)
			assertEquals("the row flipped in place, the list never reloaded",
				listOf("xyz"), model.items().map { it.item.name })
			assertTrue(model.items().single().flipped)
		}
	}

	@Test
	fun `a word found through its translation shows as the hit`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent {
			SubjectsScreen(model, {}, {}, {})
		}
		rule.waitForIdle()

		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.runOnIdle { model.openSearchField() }
		rule.runOnIdle { model.onQuerySubmit("t") }
		rule.waitForIdle()

		// the WORD is the hit, so its own row shows, not the one of the translation
		rule.onNodeWithText("xyz").assertExists()
		rule.runOnIdle {
			assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
			assertEquals("the hit is the word",
				"xyz", model.items().single().item.name)
		}

		rule.onNodeWithText("xyz").performClick()
		rule.waitForIdle()

		rule.onNodeWithText("t1").assertExists()
		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
			assertEquals("the hit flipped in place, the list never reloaded",
				1, model.items().size)
			assertTrue(model.items().single().flipped)
		}
	}
}
