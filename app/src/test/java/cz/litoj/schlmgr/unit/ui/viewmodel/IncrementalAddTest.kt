package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests of the incremental list add ([ExplorerViewModel.addItem]): one
 * new row must extend the mapped UI list by one entry, keep the existing UI
 * items' identity (no full rebuild of a thousands-row list) and keep positions
 * sequential — an insert renumbers the list like a move does.
 */
class IncrementalAddTest {

	private fun vmWith(count: Int): TestExplorerViewModel {
		val vm = TestExplorerViewModel(NavigationHistory())
		// word rows build without the database — positions run 1..count
		val rows = (1..count).map { i ->
			ItemRow(DbItem(name = "w$i", description = "", type = ItemKind.WORD), null, i, false)
		}
		vm.load(rows)
		return vm
	}

	@Test
	fun `addItem appends one row without rebuilding the mapped items`() {
		val vm = vmWith(3)
		val before = vm.listState.value.items
		val added = ItemRow(DbItem(name = "new", description = "", type = ItemKind.WORD), null, 4, false)
		vm.addItem(added)
		val after = vm.listState.value.items
		assertEquals(4, after.size)
		assertEquals(added, after.last().payload)
		// identity, not equality — a rebuild would map every row again
		before.forEachIndexed { i, _ -> assertSame(before[i], after[i]) }
	}

	@Test
	fun `an inserted row keeps positions sequential`() {
		val vm = vmWith(3)
		assertEquals(3, vm.listState.value.maxPosition)
		val inserted = ItemRow(DbItem(name = "wide", description = "", type = ItemKind.WORD), null, 99, false)
		vm.addItem(0, inserted)
		assertEquals(inserted, vm.listState.value.items.first().payload)
		assertEquals(listOf(1, 2, 3, 4), vm.listState.value.items.map { it.position })
		assertEquals(4, vm.listState.value.maxPosition)
	}

	@Test
	fun `a move keeps positions sequential and the maximum at the size`() {
		val vm = vmWith(4)
		vm.moveRow(0, 3)
		assertEquals(listOf(1, 2, 3, 4), vm.listState.value.items.map { it.position })
		assertEquals(4, vm.listState.value.maxPosition)
	}

	@Test
	fun `an empty load resets the position maximum`() {
		val vm = vmWith(3)
		vm.load(ArrayList())
		assertEquals(0, vm.listState.value.items.size)
		assertEquals(1, vm.listState.value.maxPosition)
	}
}
