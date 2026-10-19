package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests of the search-mode ladder ([ExplorerViewModel]'s [SearchUi]):
 * closing the field over results keeps them, closing it with no run behind
 * it falls back to browsing, ending the search closes the field with the
 * flow, and clearing the query leaves the flow but keeps the field. The
 * ladder is one published state — these pin its transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchModeLadderTest {

	/** The test screen — counts the content switches the doors drive, the rest stays inert. */
	private class RecordingVm(history: NavigationHistory) : TestExplorerViewModel(history) {
		var contentReloads = 0
		override fun setContent(container: DbItem?) {
			contentReloads++
		}
	}

	private fun row(name: String) =
		ItemRow(DbItem(name = name, description = "", type = ItemKind.WORD), null, 1, false)

	private fun recordingVm(): RecordingVm {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		// the field opens only inside a container — seed one into the path
		return RecordingVm(NavigationHistory()).apply {
			history.push(mutableListOf(DbItem(name = "c", description = "", type = ItemKind.CHAPTER)))
		}
	}

	/** A shown search — the field open, the hop stacked, a hit row loaded. */
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
		// nothing ended — the stacked search hop stays for back
		assertTrue(vm.history.onSearchHop())
		assertEquals("nothing reloaded", 0, vm.contentReloads)
	}

	@Test
	fun `closing the field with no run falls back to browsing`() {
		val vm = recordingVm()
		vm.openSearchField()

		vm.closeSearchField()

		assertEquals(SearchUi.OFF, vm.state.value.searchUi)
		assertEquals("nothing ran — no reload needed", 0, vm.contentReloads)
	}

	@Test
	fun `ending the search closes the field with the flow`() {
		val vm = vmWithSearchShown(recordingVm())

		vm.endSearch()

		assertEquals(SearchUi.OFF, vm.state.value.searchUi)
		// the content was reloaded — the explorer was reset
		assertEquals(1, vm.contentReloads)
		// the stacked search hop is gone — another back leaves the container
		assertFalse(vm.history.onSearchHop())
	}

	@Test
	fun `clearing the query leaves the flow but keeps the field`() {
		val vm = vmWithSearchShown(recordingVm())

		vm.onQueryChange("")

		assertEquals(SearchUi.TYPING, vm.state.value.searchUi)
		// the flow left — the hop unwound and the browse content reloaded
		assertFalse(vm.history.onSearchHop())
		assertEquals(1, vm.contentReloads)
	}
}
