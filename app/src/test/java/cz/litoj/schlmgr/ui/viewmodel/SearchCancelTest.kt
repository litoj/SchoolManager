package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.CurrentData.BackLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests of [ExplorerViewModel.cancelSearch] — back while the field is
 * open must cancel the whole search: the field closes AND the run ends (the
 * host reloads the container's full list, the stacked search hop unwinds).
 * Closing only the field (leaving the filtered list shown) was the bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchCancelTest {

	/** Records the content switch [endSearch] drives; the rest of the host stays inert. */
	private class RecordingHost : ExplorerViewModel.Host {
		var contentReloads = 0
		override fun setContent(bd: DbItem?, parent: DbItem?, pathLength: Int) { contentReloads++ }
		override fun onSelectionChanged() {}
		override fun onSearchStateChanged() {}
		override fun changedPath(skip: Int) {}
	}

	private fun row(name: String) =
		ItemRow(DbItem(name = name, description = "", type = ItemKind.WORD), null, 1, false)

	private fun vmWithSearchShown(host: RecordingHost): ExplorerViewModel {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val vm = ExplorerViewModel(BackLog().apply { clear() })
		vm.attachHost(host)
		// a shown search: the field open, the hop stacked, a hit row loaded
		vm.openSearchField()
		vm.backLog.add(false, null, vm.backLog.path)
		vm.load(listOf(row("hit")), isSearch = true)
		return vm
	}

	@Test
	fun `back in search mode closes the field and ends the search`() {
		val host = RecordingHost()
		val vm = vmWithSearchShown(host)
		assertTrue(vm.search)
		assertTrue(vm.searchFieldOpen.value)

		vm.cancelSearch()

		assertFalse("the field must close", vm.searchFieldOpen.value)
		assertFalse("the search must end", vm.search)
		// the host reloaded the browse content — the explorer was reset
		assertEquals(1, host.contentReloads)
		// the stacked search hop is gone: another back leaves the container
		assertFalse(vm.backLog.onSearchHop())
	}

	@Test
	fun `back with the field open but no run only closes the field`() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val host = RecordingHost()
		val vm = ExplorerViewModel(BackLog().apply { clear() })
		vm.attachHost(host)
		vm.openSearchField()

		vm.cancelSearch()

		assertFalse(vm.searchFieldOpen.value)
		assertEquals("nothing ran — no reload needed", 0, host.contentReloads)
	}
}
