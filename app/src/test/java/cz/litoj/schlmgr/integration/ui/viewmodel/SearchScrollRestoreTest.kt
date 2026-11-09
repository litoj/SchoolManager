package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.support.wordRows
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - back onto a stacked search restores its scroll independently of the browse list.
 * - a new query starts at the top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchScrollRestoreTest {

	private class RecordingVm(history: NavigationHistory) : TestExplorerViewModel(history) {
		val contents = ArrayList<DbItem?>()
		override fun setContent(container: DbItem?, pulseItemId: Int?) {
			contents.add(container)
		}

		fun navigate(path: MutableList<DbItem>) = openChain(path)
	}

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	private fun stackedSearch(vm: RecordingVm): List<ItemRow> {
		val hits = wordRows(3)
		vm.history.hop(null)
		vm.load(hits, isSearch = true)
		return hits
	}

	@Test
	fun `back onto a stacked search restores its own scroll`() {
		val vm = RecordingVm(NavigationHistory())
		val hits = stackedSearch(vm)
		vm.onScrollPosition(2, 60)
		val container = DbItem(id = 7, name = "c", description = "", type = ItemKind.CHAPTER)
		vm.navigate(mutableListOf(container))
		vm.load(wordRows(2))
		vm.onScrollPosition(1, 20)
		vm.contents.clear()

		vm.changedPath(1)

		assertEquals("the filtered rows return", hits, vm.listState.value.items.map { it.payload })
		assertEquals(2, vm.listState.value.anchor?.index)
		assertEquals(60, vm.listState.value.anchor?.offset)
		assertEquals("no browse reload runs", emptyList<DbItem?>(), vm.contents)
	}

	@Test
	fun `a new query starts at the top instead of the old search position`() {
		val vm = RecordingVm(NavigationHistory())
		stackedSearch(vm)
		vm.onScrollPosition(2, 60)
		val firstAnchor = vm.listState.value.anchor

		vm.submitSearch("fresh")

		assertEquals(0, vm.listState.value.anchor?.index)
		assertEquals(0, vm.listState.value.anchor?.offset)
		assertNotEquals("a top return still lands", firstAnchor, vm.listState.value.anchor)
	}
}
