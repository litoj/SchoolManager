package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.app.AppDependencies
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
 * The top bar keeps one shape in every mode.
 * - the list actions never leave it, only the strip under it changes.
 */
@RunWith(RobolectricTestRunner::class)
// a phone wide enough for every list icon, a narrow one keeps them all in More
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SubjectsTopBarModeTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase
	private lateinit var dependencies: AppDependencies

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.setQueryExecutor(Runnable::run)
			.setTransactionExecutor(Runnable::run)
			.build()
		dependencies = AppDependencies(RuntimeEnvironment.getApplication(), db)
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
	fun `the list menu stays while rows are marked`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		val model = MainExplorerViewModel(
			NavigationHistory(), Dispatchers.Unconfined,
			graphs = dependencies.graphQueries,
			editor = dependencies.itemEditor,
			itemTransferService = dependencies.itemTransfer,
			flipper = dependencies.flipper,
			settings = dependencies.settings,
			userMessages = dependencies.userMessages,
		)
		rule.setContent { SubjectsScreen(model, dependencies.settings, dependencies.userMessages, {}, {}, {}) }
		rule.waitForIdle()
		model.onItemClick(model.items().single { it.item.name == "A" })
		rule.waitForIdle()
		rule.onNodeWithContentDescription("Menu").assertExists()
		rule.onNodeWithContentDescription("Sort by").assertExists()

		model.setSelected(model.items().first(), true)
		rule.waitForIdle()

		// the nav turning into Back proves the mode, and the list menu is still up there
		rule.onNodeWithContentDescription("Back").assertExists()
		rule.onNodeWithContentDescription("Sort by").assertExists()
		rule.onNodeWithContentDescription("Select all").assertExists()
		rule.onNodeWithContentDescription("Filter by").assertExists()
	}

	@Test
	fun `the open field swaps search for the one filter pin`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		val model = MainExplorerViewModel(
			NavigationHistory(), Dispatchers.Unconfined,
			graphs = dependencies.graphQueries,
			editor = dependencies.itemEditor,
			itemTransferService = dependencies.itemTransfer,
			flipper = dependencies.flipper,
			settings = dependencies.settings,
			userMessages = dependencies.userMessages,
		)
		rule.setContent { SubjectsScreen(model, dependencies.settings, dependencies.userMessages, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithContentDescription("Search").assertExists()
		rule.onAllNodesWithContentDescription("Filter by").assertCountEquals(1)

		rule.onNodeWithContentDescription("Search").performClick()
		rule.waitForIdle()

		rule.onNode(hasSetTextAction()).assertExists()
		rule.onNodeWithContentDescription("Search").assertDoesNotExist()
		rule.onAllNodesWithContentDescription("Filter by").assertCountEquals(1)
	}
}
