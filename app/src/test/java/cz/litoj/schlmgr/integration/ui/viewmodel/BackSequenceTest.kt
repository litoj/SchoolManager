package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests of the folded back-sequence ([ExplorerViewModel.changedPath]):
 * popping the hops and then either re-showing the stacked search run (the
 * mode lands on its results) or reloading the browse list through the
 * screen's content switch. No database is needed — word rows build detached
 * and every info-strip read below lands on an empty path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackSequenceTest {

	/** The test screen — records the content switches, the rest stays inert. */
	private class RecordingVm(history: NavigationHistory) : TestExplorerViewModel(history) {
		val contents = ArrayList<DbItem?>()
		override fun setContent(container: DbItem?) {
			contents.add(container)
		}
	}

	private fun row(name: String, position: Int) =
		ItemRow(DbItem(name = name, description = "", type = ItemKind.WORD), null, position, false)

	private fun vm(): RecordingVm {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		return RecordingVm(NavigationHistory())
	}

	@Test
	fun `a back hop off a container pops the path and reloads the browse list`() {
		val vm = vm()
		val container = DbItem(id = 7, name = "c", description = "", type = ItemKind.CHAPTER)
		vm.history.hop(container)
		vm.load(listOf(row("w", 1)))

		vm.changedPath(1)

		assertTrue("the hop was popped", vm.history.path.isEmpty())
		assertEquals(listOf<DbItem?>(null), vm.contents)
		assertEquals("no search run was restored", SearchUi.OFF, vm.state.value.searchUi)
	}

	@Test
	fun `a back hop onto a stacked search run restores the filtered list`() {
		val vm = vm()
		val hits = listOf(row("hit1", 1), row("hit2", 2))
		// a shown search run — the streamed rows cached, the hop stacked
		vm.load(ArrayList())
		runBlocking {
			val rows = Channel<ItemRow>(Channel.UNLIMITED)
			hits.forEach { rows.trySend(it) }
			rows.close()
			vm.run.drainBatches(rows)
		}
		vm.history.hop(null)
		// ... then the user opened a container from a hit (a new path entry)
		// and the screen switched to its browse list
		val container = DbItem(id = 7, name = "c", description = "", type = ItemKind.CHAPTER)
		vm.history.push(mutableListOf(container))
		vm.load(listOf(row("browse", 1)))

		vm.changedPath(1)

		assertEquals("the filtered list is showing again", SearchUi.RESULTS, vm.state.value.searchUi)
		assertEquals(hits, vm.listState.value.items.map { it.payload })
		assertTrue("no browse reload ran", vm.contents.isEmpty())
	}

	@Test
	fun `a null row click is a no-op`() {
		val vm = vm()
		vm.load(listOf(row("w", 1)))

		vm.flipWord(null)
		vm.setSelected(null, true)
		vm.toggleSelect(null)

		assertFalse(vm.items().single().flipped)
		assertFalse(vm.items().single().selected)
	}
}
