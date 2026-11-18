package cz.litoj.schlmgr.integration.ui.viewmodel

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A search started from a selection reads the selected rows only.
 * - the scope holds for the session, so a refined query never widens it.
 * - leaving the search hands the whole container back.
 * - the picker selects its sources, so its search stays unlimited.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchSelectionScopeTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		// one word per chapter, both under one name stem
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		val c2 = ItemRepository.createChapter(a.id, "c2", null)
		ItemRepository.createWord(c1.id, "kotko", null, listOf("tr1" to null))
		ItemRepository.createWord(c2.id, "kotko2", null, listOf("tr2" to null))
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	/** The repository reads run inline on an unconfined dispatcher, so a run ends before the asserts. */
	private fun opened(): MainExplorerViewModel {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		model.onItemClick(model.items().single { it.item.name == "A" })
		return model
	}

	private fun names(model: MainExplorerViewModel) = model.items().map { it.item.name }.toSet()

	private fun scopeOf(model: MainExplorerViewModel) = (model.state.value.info as InfoBar.Search).selected

	@Test
	fun `a selection of one chapter searches inside it`() {
		val model = opened()
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.openSearchField()
		model.onQuerySubmit("kotk")

		assertEquals(setOf("kotko"), names(model))
		assertEquals("the strip names the scope", 1, scopeOf(model))
	}

	@Test
	fun `a query typed on of the results keeps the scope`() {
		val model = opened()
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.openSearchField()
		model.onQuerySubmit("kotk")
		model.onQuerySubmit("kotko")

		assertEquals("a run with no new marks never widens", setOf("kotko"), names(model))
		assertEquals(1, scopeOf(model))
	}

	@Test
	fun `marks on the results narrow the next run`() {
		val model = opened()
		model.openSearchField()
		model.onQuerySubmit("ko")
		assertEquals(setOf("kotko", "kotko2"), names(model))

		model.setSelected(model.items().single { it.item.name == "kotko" }, true)
		model.onQuerySubmit("kot")

		assertEquals(setOf("kotko"), names(model))
	}

	@Test
	fun `leaving the search hands the whole container back`() {
		val model = opened()
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.openSearchField()
		model.onQuerySubmit("kotk")
		model.cancelSearch()

		model.openSearchField()
		model.onQuerySubmit("kotk")

		assertEquals(setOf("kotko", "kotko2"), names(model))
		assertEquals("an unlimited run names no scope", 0, scopeOf(model))
	}

	@Test
	fun `the picker searches its whole container beside its marks`() {
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		model.onItemClick(model.items().single { it.item.name == "A" })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.openSearchField()
		model.onQuerySubmit("kotk")

		assertEquals(setOf("kotko", "kotko2"), model.items().map { it.item.name }.toSet())
	}
}
