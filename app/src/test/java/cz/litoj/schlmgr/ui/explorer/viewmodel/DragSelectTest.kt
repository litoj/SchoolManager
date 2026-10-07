package cz.litoj.schlmgr.ui.explorer.viewmodel

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drag-select spread of [ItemListViewModel]: the range follows the finger
 * from the long-pressed anchor, and pulling back towards the anchor deselects
 * the overshoot instead of leaving it selected.
 */
class DragSelectTest {

	private fun vmWith(count: Int): Pair<ItemListViewModel, List<ItemRow>> {
		val vm = ItemListViewModel()
		val rows = (0 until count).map { i ->
			// word rows build without the database; the name differs so keys differ
			ItemRow(DbItem(name = "w$i", description = "", type = ItemKind.WORD), null, i + 1, false)
		}
		vm.load(rows)
		return vm to rows
	}

	private fun selected(rows: List<ItemRow>) = rows.map { it.selected }

	/** Dragging down selects the range from the anchor to the finger. */
	@Test
	fun dragDownSelectsRange() {
		val (vm, rows) = vmWith(5)
		vm.setRangeSelected(1, 3)
		assertEquals(listOf(false, true, true, true, false), selected(rows))
	}

	/** Dragging up from the anchor selects the same spread in the other direction. */
	@Test
	fun dragUpSelectsRange() {
		val (vm, rows) = vmWith(5)
		vm.setRangeSelected(3, 1)
		assertEquals(listOf(false, true, true, true, false), selected(rows))
	}

	/** Pulling back towards the anchor deselects the rows the finger left. */
	@Test
	fun pullBackTowardsAnchorDeselectsOvershoot() {
		val (vm, rows) = vmWith(5)
		vm.setRangeSelected(1, 4)
		vm.setRangeSelected(1, 2)
		assertEquals(listOf(false, true, true, false, false), selected(rows))
	}

	/** Pulling all the way back leaves only the anchor (selected by its long-press). */
	@Test
	fun pullBackToAnchorKeepsOnlyIt() {
		val (vm, rows) = vmWith(5)
		vm.setRangeSelected(2, 4)
		vm.setRangeSelected(2, 2)
		assertEquals(listOf(false, false, true, false, false), selected(rows))
	}

	/** A row selected before the drag is untouched, even inside the swept range. */
	@Test
	fun preDragSelectionSurvivesPullBack() {
		val (vm, rows) = vmWith(5)
		rows[4].selected = true // an earlier long-press, outside the drag
		vm.setRangeSelected(1, 3)
		vm.setRangeSelected(1, 1)
		assertEquals(listOf(false, true, false, false, true), selected(rows))
	}

	/** After the drag ends, its selection is the baseline of the next drag. */
	@Test
	fun endSelectMakesSelectionTheBaseline() {
		val (vm, rows) = vmWith(5)
		vm.setRangeSelected(1, 3)
		vm.endRangeSelect()
		vm.setRangeSelected(1, 1)
		assertEquals(listOf(false, true, true, true, false), selected(rows))
	}
}
