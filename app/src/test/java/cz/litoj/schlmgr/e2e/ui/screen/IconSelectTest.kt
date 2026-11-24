package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import cz.litoj.schlmgr.app.AppDependencies
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.component.RowIconTag
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
 * The type icon of a row marks it wherever the list selects or gathers.
 * - a browse icon tap starts a selection, and the picker icon toggles its pick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconSelectTest {

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

	@Test
	fun `picker - the icon picks the row, the row tap still flips the word`() {
		val model = PickerViewModel(
			NavigationHistory(), Dispatchers.Unconfined,
			graphs = dependencies.graphQueries,
			transfer = dependencies.itemTransfer,
			userMessages = dependencies.userMessages,
		)
		rule.setContent { PickerScreen(model, dependencies.settings, dependencies.userMessages, {}) }
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
			assertTrue("the icon picked the word",
				model.items().single { it.item.name == "xyz" }.batched)
		}
	}

	@Test
	fun `browse - an icon tap starts the selection`() {
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
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()

		rule.onAllNodesWithTag(RowIconTag, useUnmergedTree = true)[1].performClick()
		rule.waitForIdle()

		rule.runOnIdle {
			assertTrue("the icon tap starts the selection",
				model.items().single { it.item.name == "w2" }.selected)
			// the icon marks, so the word keeps its shown side
			assertTrue("the icon tap never flips the word",
				model.items().single { it.item.name == "w2" }.flipped)
		}
	}

	@Test
	fun `selection - a tap on the icon toggles the row`() {
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
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()
		// a long-press starts the selection, the icon then toggles the second word
		rule.onNodeWithText("t1").performTouchInput {
			down(center)
			advanceEventTime(1_000)
			up()
		}
		rule.waitForIdle()
		rule.onAllNodesWithTag(RowIconTag, useUnmergedTree = true)[1].performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertTrue("the row tap added the second word to the selection",
				model.items().single { it.item.name == "w2" }.selected)
			assertTrue("the first selection stays",
				model.items().single { it.item.name == "xyz" }.selected)
			// the icon toggles, so the word keeps its side
			assertTrue("the icon never flips during a selection",
				model.items().single { it.item.name == "w2" }.flipped)
		}
	}
}
