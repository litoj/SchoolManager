package cz.litoj.schlmgr.ui.viewmodel

/**
 * Selection machine — row marks and drag-select baseline.
 * - host owns rows — this class decides marks.
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
		row.selected = selected
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
		// Drag over results would select under paste strip.
		if (host.pasteArmed()) return
		setRangeSelected(from, to)
	}

	fun endRangeSelect() {
		dragSelect = null
	}

	fun selectAll(on: Boolean) {
		host.models.forEach { it.selected = on }
		dragSelect = null
		host.remap()
	}

	fun onSelectAllToggle() {
		val models = host.models
		if (models.count { it.selected } < models.size) selectAll(true)
		else clearSelection()
	}

	fun clearSelection() {
		host.models.forEach { it.selected = false }
		dragSelect = null
		host.remap()
	}

	/**
	 * One drag-select step — the range is [anchor]..[to].
	 * - pulled-back rows return to pre-drag state.
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
				models[i].selected = want
				changed = true
			}
		}
		if (changed) host.remap()
	}

	fun reset() {
		dragSelect = null
	}
}
