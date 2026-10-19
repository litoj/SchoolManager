package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.wordVm
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The select-all toggle of [ExplorerViewModel]'s selection — one selected row
 * grows the selection to the whole list, a second press drops it entirely.
 */
class SelectAllTest {

	@Test
	fun `the select-all toggle marks all or drops the selection`() {
		val vm = wordVm(3)

		vm.setSelected(vm.items()[0], true)
		vm.onSelectAllToggle()
		assertEquals(3, vm.selectionCount())

		vm.onSelectAllToggle()
		assertEquals(0, vm.selectionCount())
	}

	@Test
	fun `the toggle without a selection selects the whole list`() {
		val vm = wordVm(3)

		vm.onSelectAllToggle()

		assertEquals(3, vm.selectionCount())
		assertTrue("every row carries the mark", vm.items().all(ItemRow::selected))
	}
}
