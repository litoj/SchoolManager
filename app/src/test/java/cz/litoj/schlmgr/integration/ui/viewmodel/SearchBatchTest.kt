package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchRun
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests of the search-run batching ([SearchRun.drainBatches], driven
 * through the view model so the host wiring is covered too): a run longer
 * than [SearchRun.SEARCH_BATCH] streams in full batches plus a tail, keeping
 * every row in order with the running result count on the info strip. An
 * empty drain leaves the list alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchBatchTest {

	private fun vm(): TestExplorerViewModel {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		return TestExplorerViewModel(NavigationHistory())
	}

	private fun rows(count: Int) = (1..count).map { i ->
		ItemRow(DbItem(name = "hit$i", description = "", type = ItemKind.WORD), null, i, false)
	}

	private fun drain(vm: TestExplorerViewModel, rows: List<ItemRow>) {
		runBlocking {
			val channel = Channel<ItemRow>(Channel.UNLIMITED)
			rows.forEach { channel.trySend(it) }
			channel.close()
			vm.run.drainBatches(channel)
		}
	}

	@Test
	fun `a long run drains in full batches plus a tail`() {
		val vm = vm()
		vm.load(ArrayList(), isSearch = true)
		val expected = rows(2 * SearchRun.SEARCH_BATCH + 5)

		drain(vm, expected)

		assertEquals(expected, vm.listState.value.items.map { it.payload })
		assertEquals(expected.size, vm.listState.value.maxPosition)
		assertEquals("the running count reached the strip",
			InfoBar.Search(expected.size, (vm.state.value.info as InfoBar.Search).chapters),
			vm.state.value.info)
	}

	@Test
	fun `an empty drain leaves the list alone`() {
		val vm = vm()
		vm.load(rows(2))

		drain(vm, emptyList())

		assertEquals(2, vm.listState.value.items.size)
	}

	@Test
	fun `a batch after a renumbered list continues the sequence`() {
		val vm = vm()
		// a mid-stream sort renumbers the shown rows 1..n — the next batch
		// must continue from the live size, not its stale run-global positions
		vm.load(rows(2), isSearch = true)
		val batch = rows(2).map { it.item.copy(id = 100 + it.item.id.toInt()) }
			.map { ItemRow(it, null, 99, false) }

		drain(vm, batch)

		assertEquals(listOf(1, 2, 3, 4),
			vm.listState.value.items.map { it.payload!!.position })
		assertEquals(4, vm.listState.value.maxPosition)
	}
}
