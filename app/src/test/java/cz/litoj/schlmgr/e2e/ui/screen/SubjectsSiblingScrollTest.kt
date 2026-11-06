package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
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
 * - a sibling switch opens the new chapter at its top.
 * - the way back restores the scrolled place and the flip side.
 * - a top row composes only while the list sits at its start, so the node
 * answers for the position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsSiblingScrollTest {

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

	/** Seeds two chapters with forty words, a note in the first, opened scrolled deep. */
	private fun scrolledFirstChapter(): MainExplorerViewModel {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		val c2 = ItemRepository.createChapter(a.id, "c2", null, 1)
		repeat(40) { i ->
			ItemRepository.createWord(c1.id, "w$i", null, listOf("t$i" to null))
			ItemRepository.createWord(c2.id, "v$i", null, listOf("u$i" to null))
		}
		ItemRepository.createNote(c1.id, "n1", "note")
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		model.onItemClick(model.items().single { it.item.name == "A" })
		rule.waitForIdle()
		model.onItemClick(model.items().single { it.item.name == "c1" })
		rule.waitForIdle()
		rule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
		rule.waitForIdle()
		return model
	}

	/** Three siblings with a custom descending alpha sort unlike repository order. */
	private fun customSortedModel(): MainExplorerViewModel {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "alpha", null)
		ItemRepository.createChapter(a.id, "zulu", null)
		ItemRepository.createChapter(a.id, "mid", null)
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		model.onItemClick(model.items().single { it.item.name == "A" })
		rule.waitForIdle()
		model.onMenuItemClick(cz.litoj.schlmgr.ui.screen.subjects.MenuId.SORT_ALPHA_ZA)
		rule.waitForIdle()
		assertEquals(listOf("zulu", "mid", "alpha"), model.items().map { it.item.name })
		return model
	}

	@Test
	fun `a custom parent sort survives opening a child and returning`() {
		val model = customSortedModel()
		model.onItemClick(model.items().single { it.item.name == "zulu" })
		rule.waitForIdle()

		assertTrue(model.onBackRequested() == cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel.BackOutcome.HANDLED)
		assertEquals(listOf("zulu", "mid", "alpha"), model.items().map { it.item.name })
	}

	@Test
	fun `siblings advance and retreat in the custom parent order`() {
		val model = customSortedModel()
		model.onItemClick(model.items().single { it.item.name == "zulu" })
		rule.waitForIdle()

		model.openSibling(1)
		rule.waitForIdle()
		assertEquals("mid", model.history.path.last().name)
		model.openSibling(-1)
		rule.waitForIdle()
		assertEquals("zulu", model.history.path.last().name)
	}

	@Test
	fun `original order clears the remembered parent sort`() {
		val model = customSortedModel()

		model.onMenuItemClick(cz.litoj.schlmgr.ui.screen.subjects.MenuId.SORT_DEFAULT)

		assertEquals(listOf("alpha", "zulu", "mid"), model.items().map { it.item.name })
	}

	@Test
	fun `a sibling switch opens the new chapter at its top`() {
		val model = scrolledFirstChapter()
		// the words show their translations, the scrolled place sits deep in the chapter
		rule.onNodeWithText("t0").assertDoesNotExist()

		model.openSibling(1)
		rule.waitForIdle()

		rule.onNodeWithText("u0").assertExists()
	}

	@Test
	fun `the way back to a sibling restores its scrolled place`() {
		val model = scrolledFirstChapter()

		model.openSibling(1)
		rule.waitForIdle()
		model.openSibling(-1)
		rule.waitForIdle()

		// the first chapter comes back at its scrolled place, its top stays away
		rule.onNodeWithText("t30").assertExists()
		rule.onNodeWithText("t0").assertDoesNotExist()
	}

	@Test
	fun `a flipped word keeps its side through a sibling round trip`() {
		val model = scrolledFirstChapter()
		// a tap flips the word onto its name side
		rule.onNodeWithText("t30").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("w30").assertExists()

		model.openSibling(1)
		rule.waitForIdle()
		model.openSibling(-1)
		rule.waitForIdle()

		rule.onNodeWithText("w30").assertExists()
		rule.onNodeWithText("t30").assertDoesNotExist()
	}

	@Test
	fun `an item edit keeps the scrolled place and the flip sides`() {
		val model = scrolledFirstChapter()
		rule.onNodeWithText("t30").performClick()
		rule.waitForIdle()
		val note = model.items().single { it.item.name == "n1" }

		assertTrue(model.commitItemEdit(note, CreatorResult("n2", "new", emptyList(), emptyList())))
		rule.waitForIdle()

		// the flip side survived the edit and the list never left its place
		rule.onNodeWithText("w30").assertExists()
		rule.onNodeWithText("t30").assertDoesNotExist()
		// the edited note keeps its slot at the end of the list
		rule.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
		rule.waitForIdle()
		rule.onNodeWithText("n2").assertExists()
	}
}
