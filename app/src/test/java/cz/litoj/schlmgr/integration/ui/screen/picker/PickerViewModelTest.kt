package cz.litoj.schlmgr.integration.ui.screen.picker

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ExplorerMenuKind
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.MenuId
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
 * - the picker gathers its picks like an armed paste, so they span folders.
 * - a search hop stays stacked, so back returns the unselected results.
 * - its top-bar menu follows the open list, its file doors stay shut.
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
		db.close()
		ItemRepository.resetForTest()
	}

	private fun vm(source: NavigationHistory = NavigationHistory()): PickerViewModel {
		val model = PickerViewModel(source, Dispatchers.Unconfined)
		model.clock = { now }
		return model
	}

	private fun seed(): Int {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "c1", null, 0)
		return a.id
	}

	@Test
	fun `the picker opens at the explorer position and never moves the explorer`() {
		seed()
		val explorer = NavigationHistory()
		explorer.hop(ItemRepository.subjects().single())
		val model = vm(explorer)

		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertEquals(listOf("A", "c1"), model.history.path.map { it.name })
		assertEquals("the explorer keeps its position", listOf("A"), explorer.path.map { it.name })
	}

	@Test
	fun `a second picker opens at the explorer position again`() {
		seed()
		val explorer = NavigationHistory()
		explorer.hop(ItemRepository.subjects().single())
		val first = vm(explorer)
		first.onItemClick(first.items().single { it.item.name == "c1" })

		val second = vm(explorer)

		assertEquals(listOf("A"), second.state.value.breadcrumbs)
	}

	@Test
	fun `back unwinds the seeded position to its root before the exit`() {
		val aId = seed()
		val explorer = NavigationHistory()
		explorer.hop(ItemRepository.subjects().single())
		explorer.hop(ItemRepository.children(aId).single())
		val model = vm(explorer)

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(listOf("A"), model.history.path.map { it.name })
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertTrue("the root of the seed is the exit root", model.history.path.isEmpty())
		// the picker root leaves on one press, it is no app exit
		assertEquals(ExplorerViewModel.BackOutcome.EXIT, model.onBackRequested())
	}

	@Test
	fun `back pops a path level and keeps the picks`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertTrue("the back press left the subject", model.history.path.isEmpty())
		assertEquals(listOf("c1"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `one back at the root leaves the picker`() {
		seed()
		val model = vm()

		assertEquals(ExplorerViewModel.BackOutcome.EXIT, model.onBackRequested())
	}

	@Test
	fun `the picker gathers its picks instead of selecting`() {
		seed()
		val model = vm()
		val a = model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER }

		assertTrue("the icons gather from the start", model.listState.value.gathering)
		model.setSelected(a, true)
		assertTrue(a.batched)
		assertFalse("no selection mode runs", model.listState.value.selectionActive)
	}

	@Test
	fun `opening a chapter keeps the picks and marks them on return`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.onItemClick(model.items().single { it.item.name == "c1" })
		assertEquals(listOf("A", "c1"), model.state.value.breadcrumbs)
		assertEquals(listOf("c1"), model.batchRows().map { it.item.name })

		model.onBackRequested()
		assertTrue("the fresh row of the pick shows marked", model.items().single { it.item.name == "c1" }.batched)
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
	fun `restored search results come back with their picks`() {
		seed()
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.onItemClick(model.items().single { it.item.name == "c1" })
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)

		assertTrue("the pick outlived the trip", model.items().single { it.item.name == "c1" }.batched)
	}

	@Test
	fun `select all in one folder keeps the picks of another`() {
		val aId = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" && it.item.type == ItemKind.CHAPTER })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.onItemClick(model.items().single { it.item.name == "c1" })

		model.onSelectAllToggle()

		assertTrue("the shown rows joined the picks", model.items().all { it.batched })
		assertEquals(listOf("c1", "w1", "w2"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `a sibling switch opens the next subject and keeps the picks`() {
		seed()
		ItemRepository.createSubject("B", null)
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.openSibling(1)

		assertEquals(listOf("B"), model.history.path.map { it.name })
		assertEquals(listOf("c1"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `the picker menu follows its own list`() {
		seed()
		val model = vm()
		assertEquals("the seeded root stays a browse root", ExplorerMenuKind.ROOT, model.menuKind)

		model.onItemClick(model.items().single { it.item.name == "A" })
		assertEquals("a browse folder keeps the folder menu", ExplorerMenuKind.CONTAINER, model.menuKind)

		model.openSearchField()
		model.onQuerySubmit("c1")
		assertEquals("a search run keeps the search menu", ExplorerMenuKind.SEARCH, model.menuKind)
	}

	@Test
	fun `the picker returns its browse list to the original order`() {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "b1", null, 0)
		ItemRepository.createChapter(a.id, "a2", null, 1)
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == "A" })

		assertTrue(model.onMenuItemClick(MenuId.SORT_ALPHA_AZ))
		assertEquals(listOf("a2", "b1"), model.items().map { it.item.name })

		assertTrue("the picker answers its own original order", model.onMenuItemClick(MenuId.SORT_DEFAULT))

		assertEquals(listOf("b1", "a2"), model.items().map { it.item.name })
	}
}
