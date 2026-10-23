package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.wordVm
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
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
	fun `after the drag ends its selection is the baseline of the next drag`() {
		val vm = wordVm(5)
		vm.setRangeSelected(1, 3)
		vm.endRangeSelect()
		vm.setRangeSelected(1, 1)
		assertEquals(listOf(false, true, true, true, false), selected(vm.items()))
	}
}
