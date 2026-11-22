package cz.litoj.schlmgr.ui.viewmodel

/**
 * The selection machine: row marks and the drag-select baseline.
 * - the host owns the rows, and this class decides the marks.
 * - a mark is display state of the row, so marking publishes no list.
 */
internal class Selection(private val host: Host) {

	interface Host {

		val models: List<ItemRow>
	}

	private var dragSelect: Pair<Int, BooleanArray>? = null

	private fun setRowSelected(item: ItemRow?, selected: Boolean) {
		val row = item ?: return
		if (row.selected == selected) return
		row.select(selected)
		dragSelect = null
	}

	fun toggleSelect(item: ItemRow?) {
		val row = item ?: return
		setRowSelected(row, !row.selected)
	}

	fun setSelected(item: ItemRow?, selected: Boolean) {
		setRowSelected(item, selected)
	}

	fun endRangeSelect() {
		dragSelect = null
	}

	fun selectAll(on: Boolean) {
		host.models.forEach { it.select(on) }
		dragSelect = null
	}

	fun onSelectAllToggle() {
		val models = host.models
		if (models.count { it.selected } < models.size) selectAll(true)
		else clearSelection()
	}

	fun clearSelection() {
		host.models.forEach { it.select(false) }
		dragSelect = null
	}

	/**
	 * Drops the marks of rows outside the shown list.
	 * - a restore loads them fresh right after, so nothing publishes here.
	 */
	fun discard(rows: List<ItemRow>) {
		rows.forEach { it.select(false) }
	}

	/**
	 * One drag-select step, where the range is [anchor]..[to].
	 * - pulled-back rows return to their pre-drag state.
	 * - [selectedTo] paints an icon press state instead of only adding marks.
	 */
	fun setRangeSelected(anchor: Int, to: Int, selectedTo: Boolean? = null) {
		val models = host.models
		if (models.isEmpty()) return
		var snapshot = dragSelect
		if (snapshot == null || snapshot.first != anchor) {
			snapshot = anchor to BooleanArray(models.size) { models[it].selected }
			dragSelect = snapshot
		}
		val lo = anchor.coerceIn(0, models.size - 1)
		val hi = to.coerceIn(0, models.size - 1)
		val before = snapshot.second
		for (i in models.indices) {
			val inside = i in minOf(lo, hi)..maxOf(lo, hi)
			val want = if (inside) selectedTo ?: true else (i < before.size && before[i])
			if (models[i].selected != want) models[i].select(want)
		}
	}

	fun reset() {
		dragSelect = null
	}
}

/**
 * Every selected row in shown order, which one reorder gathers and moves together.
 * - an unselected anchor moves alone, since only a selected press starts a reorder.
 */
internal fun selectedRows(selected: List<Boolean>, anchor: Int): List<Int> =
	selected.indices.filter { selected[it] }
		.ifEmpty { listOf(anchor).filter { it in selected.indices } }
