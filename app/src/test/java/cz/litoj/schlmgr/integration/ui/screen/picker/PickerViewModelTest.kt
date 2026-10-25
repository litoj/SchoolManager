package cz.litoj.schlmgr.integration.ui.screen.picker

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the picker selects like the subjects screen, no mode of its own.
 * - a search hop stays stacked, so back returns the unselected results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PickerViewModelTest {

	private lateinit var db: ItemDatabase
	private var now = 1_000_000L

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
		// the saved position of the picker is process-wide, so leave the root for the next class
		PickerViewModel.resetSavedHistory()
		db.close()
	}

	private fun vm(): PickerViewModel {
		val model = PickerViewModel(Dispatchers.Unconfined)
		model.clock = { now }
		// the browsing position of the picker survives its screens by design, so walk
		// it back to the root where every test starts at the subjects list
		while (model.history.path.isNotEmpty()) model.onBackRequested()
		return model
	}

	private fun seed(): Int {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "c1", null, 0)
		return a.id
	}

	@Test
	fun `back clears the selection before it pops a path level`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertFalse("the selection left, the list stayed", model.items().any { it.selected })

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertTrue("the back press left the subject", model.history.path.isEmpty())
	}

	@Test
	fun `back at the root exits the picker through the window`() {
		seed()
		val model = vm()

		assertEquals(ExplorerViewModel.BackOutcome.EXIT_HINT, model.onBackRequested())
		now += 2_000
		assertEquals(ExplorerViewModel.BackOutcome.EXIT, model.onBackRequested())
	}

	@Test
	fun `the selection mode follows the selected rows, not the picker`() {
		seed()
		val model = vm()

		assertFalse("no row is selected, so no selection mode", model.listState.value.selectionActive)
		model.setSelected(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER }, true)
		assertTrue(model.listState.value.selectionActive)
	}

	@Test
	fun `opening a chapter drops the selection with the list`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertFalse("a new list starts unselected", model.items().any { it.selected })
		assertEquals(listOf("A", "c1"), model.state.value.breadcrumbs)
	}

	@Test
	fun `a search-hit chapter opens its chain and back restores the results`() {
		val aId = seed()
		// a chapter inside c1, where the chain of the hit runs two levels under the
		// searched subject, so a plain hop onto the hit would lose c1
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createChapter(c1.id, "c2", null, 0)
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.openSearchField()
		model.onQuerySubmit("c2")

		model.onItemClick(model.items().single { it.item.name == "c2" })

		assertEquals(listOf("A", "c1", "c2"), model.history.path.map { it.name })
		assertEquals(listOf("A", "c1", "c2"), model.state.value.breadcrumbs)
		assertEquals("the open left the search for browsing", SearchUi.OFF, model.state.value.searchUi)

		// the search hop stayed stacked under the opened chain, so the back press
		// returns the results, not the browse list of the subject
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)
		assertEquals(listOf("c2"), model.items().map { it.item.name })
	}

	@Test
	fun `restored search results come back unselected`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.onItemClick(model.items().single { it.item.name == "c1" })
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)

		assertFalse("the selection belonged to the left list — none carries over",
			model.items().any { it.selected })
	}
}
