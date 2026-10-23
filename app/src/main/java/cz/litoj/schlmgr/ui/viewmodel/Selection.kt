package cz.litoj.schlmgr.ui.viewmodel

/**
 * The selection machine: row marks and the drag-select baseline.
 * - the host owns the rows, and this class decides the marks.
 */
internal class Selection(private val host: Host) {

	interface Host {

		val models: List<ItemRow>

		fun remap()

		fun pasteArmed(): Boolean
	}

	private var dragSelect: Pair<Int, BooleanArray>? = null

	private fun setRowSelected(item: ItemRow?, selected: Boolean) {
		val row = item ?: return
		if (row.selected == selected) return
		row.select(selected)
		dragSelect = null
		host.remap()
	}

	fun toggleSelect(item: ItemRow?) {
		val row = item ?: return
		setRowSelected(row, !row.selected)
	}

	fun setSelected(item: ItemRow?, selected: Boolean) {
		setRowSelected(item, selected)
	}

	fun selectRange(from: Int, to: Int) {
		// a drag over the results would select under the paste strip.
		if (host.pasteArmed()) return
		setRangeSelected(from, to)
	}

	fun endRangeSelect() {
		dragSelect = null
	}

	fun selectAll(on: Boolean) {
		host.models.forEach { it.select(on) }
		dragSelect = null
		host.remap()
	}

	fun onSelectAllToggle() {
		val models = host.models
		if (models.count { it.selected } < models.size) selectAll(true)
		else clearSelection()
	}

	fun clearSelection() {
		host.models.forEach { it.select(false) }
		dragSelect = null
		host.remap()
	}

	/**
	 * Drops the marks of rows outside the shown list.
	 * - a restore loads them fresh right after, so no remap runs here.
	 */
	fun discard(rows: List<ItemRow>) {
		rows.forEach { it.select(false) }
	}

	/**
	 * One drag-select step, where the range is [anchor]..[to].
	 * - pulled-back rows return to their pre-drag state.
	 */
	fun setRangeSelected(anchor: Int, to: Int) {
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
		var changed = false
		for (i in models.indices) {
			val want = i in minOf(lo, hi)..maxOf(lo, hi) || (i < before.size && before[i])
			if (models[i].selected != want) {
				models[i].select(want)
				changed = true
			}
		}
		if (changed) host.remap()
	}

	fun reset() {
		dragSelect = null
	}
}
