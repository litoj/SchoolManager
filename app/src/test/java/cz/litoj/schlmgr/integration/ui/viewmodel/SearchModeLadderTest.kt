package cz.litoj.schlmgr.integration.ui.viewmodel

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchModeLadderTest {

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

	/** The test screen: counts the content switches the doors drive, the rest stays inert. */
	private class RecordingVm(history: NavigationHistory, db: ItemDatabase) :
		TestExplorerViewModel(history, GraphQueries(db)) {
		var contentReloads = 0
		override fun setContent(container: DbItem?, pulseItemId: Int?) {
			contentReloads++
		}
	}

	private fun row(name: String) =
		ItemRow(DbItem(name = name, description = "", type = ItemKind.WORD), null, 1)

	private fun recordingVm(): RecordingVm {
		// the field opens only inside a container, so seed one into the path
		return RecordingVm(NavigationHistory(), db).apply {
			history.push(mutableListOf(DbItem(name = "c", description = "", type = ItemKind.CHAPTER)))
		}
	}

	private fun vmWithSearchShown(vm: RecordingVm): RecordingVm {
		vm.openSearchField()
		vm.history.hop(null)
		vm.load(listOf(row("hit")), isSearch = true)
		return vm
	}

	@Test
	fun `closing the field over results keeps them`() {
		val vm = vmWithSearchShown(recordingVm())
		assertEquals(SearchUi.TYPING, vm.state.value.searchUi)

		vm.closeSearchField()

		assertEquals("the field closed onto its results", SearchUi.RESULTS, vm.state.value.searchUi)
		// nothing ended, so the stacked search hop stays for back
		assertTrue(vm.history.onSearchHop())
		assertEquals("nothing reloaded", 0, vm.contentReloads)
	}

	@Test
	fun `a filter keeps the results field open`() {
		val vm = vmWithSearchShown(recordingVm())
		vm.closeSearchField()
		assertEquals(SearchUi.RESULTS, vm.state.value.searchUi)

		vm.applyFilter(Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.CHAPTER)))

		assertEquals(SearchUi.RESULTS, vm.state.value.searchUi)
	}

	@Test
	fun `closing the field with no run falls back to browsing`() {
		val vm = recordingVm()
		vm.openSearchField()

		vm.closeSearchField()

		assertEquals(SearchUi.OFF, vm.state.value.searchUi)
		assertEquals("nothing ran, no reload needed", 0, vm.contentReloads)
	}

	@Test
	fun `ending the search closes the field with the flow`() {
		val vm = vmWithSearchShown(recordingVm())

		vm.endSearch()

		assertEquals(SearchUi.OFF, vm.state.value.searchUi)
		// the content was reloaded, so the explorer was reset
		assertEquals(1, vm.contentReloads)
		// the stacked search hop is gone, so another back leaves the container
		assertFalse(vm.history.onSearchHop())
	}

	@Test
	fun `clearing the query leaves the flow but keeps the field`() {
		val vm = vmWithSearchShown(recordingVm())

		vm.onQueryChange("")

		assertEquals(SearchUi.TYPING, vm.state.value.searchUi)
		// the flow left, the hop unwound and the browse content reloaded
		assertFalse(vm.history.onSearchHop())
		assertEquals(1, vm.contentReloads)
	}
}
