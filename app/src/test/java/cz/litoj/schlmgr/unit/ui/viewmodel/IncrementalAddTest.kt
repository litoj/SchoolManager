package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.wordVm
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * - one new row must extend the mapped UI list by one entry.
 * - it must keep the identity of the existing UI items, with no full rebuild.
 * - positions must stay sequential.
 * - an insert thus renumbers the list like a move does.
 */
class IncrementalAddTest {

	@Test
	fun `addItem appends one row without rebuilding the mapped items`() {
		val vm = wordVm(3)
		val before = vm.listState.value.items
		val added = ItemRow(DbItem(name = "new", description = "", type = ItemKind.WORD), null, 4, false)
		vm.addItem(added)
		val after = vm.listState.value.items
		assertEquals(4, after.size)
		assertEquals(added, after.last().payload)
		// identity, not equality, because a rebuild would map every row again
		before.forEachIndexed { i, _ -> assertSame(before[i], after[i]) }
	}

	@Test
	fun `an inserted row keeps positions sequential`() {
		val vm = wordVm(3)
		assertEquals(3, vm.listState.value.maxPosition)
		val inserted = ItemRow(DbItem(name = "wide", description = "", type = ItemKind.WORD), null, 99, false)
		vm.addItem(0, inserted)
		assertEquals(inserted, vm.listState.value.items.first().payload)
		assertEquals(listOf(1, 2, 3, 4), vm.listState.value.items.map { it.position })
		assertEquals(4, vm.listState.value.maxPosition)
	}

	@Test
	fun `a move keeps positions sequential and the maximum at the size`() {
		val vm = wordVm(4)
		vm.moveRow(0, 3)
		assertEquals(listOf(1, 2, 3, 4), vm.listState.value.items.map { it.position })
		assertEquals(4, vm.listState.value.maxPosition)
	}

	@Test
	fun `an empty load resets the position maximum`() {
		val vm = wordVm(3)
		vm.load(ArrayList())
		assertEquals(0, vm.listState.value.items.size)
		assertEquals(1, vm.listState.value.maxPosition)
	}
}
