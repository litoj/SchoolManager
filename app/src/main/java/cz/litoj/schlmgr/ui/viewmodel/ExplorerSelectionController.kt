package cz.litoj.schlmgr.ui.viewmodel

/** Selection and cross-folder gathering, with one gesture policy per row. */
internal class ExplorerSelectionController(private val host: Host) {
	interface Host {
		val models: List<ItemRow>
		fun openContainer(): ItemRow?
		fun clumpsContainer(): Boolean
		fun gathering(): Boolean
		fun batchChanged()
	}

	private inner class SelectionHost : Selection.Host {
		override val models get() = host.models
	}

	private inner class GatheredHost : GatheredRows.Host {
		override val models get() = host.models
		override fun openContainer() = host.openContainer()
		override fun clumpsContainer() = host.clumpsContainer()
		override fun batchChanged() = host.batchChanged()
	}

	private val selection = Selection(SelectionHost())
	private val batch = GatheredRows(GatheredHost())

	val rows: List<ItemRow> get() = batch.rows
	val dragging: Boolean get() = batch.dragging
	val armed: Boolean get() = batch.armed

	fun gatheredRowsForClose(): List<ItemRow> {
		batch.viewLeft(host.models)
		return batch.rows
	}

	fun startBatch(rows: List<ItemRow>) = batch.start(rows)
	fun clearBatch() = batch.clear()

	fun onItemLongClick(item: ItemRow?) {
		val row = item ?: return
		if (host.gathering()) {
			batch.begin()
			batch.toggle(row)
		} else {
			batch.endRange()
			if (!row.selected) selection.setSelected(row, true)
		}
	}

	fun toggleSelect(item: ItemRow?, gathering: Boolean) {
		val row = item ?: return
		if (gathering) batch.toggle(row) else selection.toggleSelect(row)
	}

	fun setSelected(item: ItemRow?, selected: Boolean, gathering: Boolean) {
		val row = item ?: return
		if (gathering) batch.hold(row, selected) else selection.setSelected(row, selected)
	}

	fun selectRange(from: Int, to: Int) =
		if (batch.armed) batch.range(from, to) else selection.setRangeSelected(from, to)

	fun endRangeSelect() {
		val dragged = batch.dragging
		batch.endRange()
		selection.endRangeSelect()
		if (dragged) host.batchChanged()
	}

	fun setRangeSelected(anchor: Int, to: Int, selectedTo: Boolean? = null) =
		selection.setRangeSelected(anchor, to, selectedTo)
	fun selectAll(on: Boolean) = selection.selectAll(on)
	fun onSelectAllToggle(gathering: Boolean) =
		if (gathering) batch.toggleAll() else selection.onSelectAllToggle()
	fun clearSelection() = selection.clearSelection()
	fun discard(rows: List<ItemRow>) = selection.discard(rows)

	fun resetDragSelect() {
		batch.endRange()
		selection.reset()
	}

	fun rowsMarked(rows: List<ItemRow>) = batch.markShown(rows)
	fun viewShown(rows: List<ItemRow>) = batch.viewShown(rows)
	fun viewLeft(rows: List<ItemRow>) = batch.viewLeft(rows)
}
