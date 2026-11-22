package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.wordVm
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.selectedRows
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - pulling back towards the anchor deselects the overshoot.
 * - the overshoot thus does not stay selected.
 */
class DragSelectTest {

	private fun selected(rows: List<ItemRow>) = rows.map { it.selected }

	@Test
	fun `dragging down selects the range from the anchor to the finger`() {
		val vm = wordVm(5)
		vm.setRangeSelected(1, 3)
		assertEquals(listOf(false, true, true, true, false), selected(vm.items()))
	}

	@Test
	fun `dragging up from the anchor selects the same spread`() {
		val vm = wordVm(5)
		vm.setRangeSelected(3, 1)
		assertEquals(listOf(false, true, true, true, false), selected(vm.items()))
	}

	@Test
	fun `pulling back towards the anchor deselects the rows the finger left`() {
		val vm = wordVm(5)
		vm.setRangeSelected(1, 4)
		vm.setRangeSelected(1, 2)
		assertEquals(listOf(false, true, true, false, false), selected(vm.items()))
	}

	@Test
	fun `pulling all the way back leaves only the anchor`() {
		val vm = wordVm(5)
		vm.setRangeSelected(2, 4)
		vm.setRangeSelected(2, 2)
		assertEquals(listOf(false, false, true, false, false), selected(vm.items()))
	}

	@Test
	fun `a row selected before the drag survives the pull back`() {
		val vm = wordVm(5)
		vm.items()[4].select(true) // an earlier long-press, outside the drag
		vm.setRangeSelected(1, 3)
		vm.setRangeSelected(1, 1)
		assertEquals(listOf(false, true, false, false, true), selected(vm.items()))
	}

	@Test
	fun `an icon drag deselects every covered row and restores untouched marks`() {
		val vm = wordVm(5)
		vm.items()[1].select(true)
		vm.items()[2].select(true)
		vm.items()[4].select(true)

		vm.setRangeSelected(1, 3, false)

		assertEquals(listOf(false, false, false, false, true), selected(vm.items()))
	}

	@Test
	fun `after the drag ends its selection is the baseline of the next drag`() {
		val vm = wordVm(5)
		vm.setRangeSelected(1, 3)
		vm.endRangeSelect()
		vm.setRangeSelected(1, 1)
		assertEquals(listOf(false, true, true, true, false), selected(vm.items()))
	}

	@Test
	fun `every selected row joins the move in shown order`() {
		val selected = listOf(true, false, true, false, true)

		assertEquals(listOf(0, 2, 4), selectedRows(selected, 2))
	}

	@Test
	fun `an unselected anchor moves alone without a selection`() {
		assertEquals(listOf(2), selectedRows(listOf(false, false, false), 2))
	}
}
