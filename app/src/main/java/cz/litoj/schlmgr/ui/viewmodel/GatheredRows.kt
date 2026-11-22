package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The rows gathered across folders, which every list marks wherever it shows them.
 * - the armed paste and the picker gather through it.
 * - the host owns the shown rows, and this class decides their marks.
 * - a drag carries the mark its press left on the dragged row.
 * - where the host clumps, a container and its wholly held view stand for each other.
 */
internal class GatheredRows(private val host: Host) {

	interface Host {

		val models: List<ItemRow>

		/** The row of the container the list shows, null while the root or a search shows. */
		fun openContainer(): ItemRow?

		/** Whether a wholly held container stands for its rows, the picker of test sources. */
		fun clumpsContainer(): Boolean

		/** The gather changed, an emptied one included. */
		fun batchChanged()
	}

	var rows by mutableStateOf<List<ItemRow>>(emptyList())
		private set

	/** The batch at the start of the running drag, and the mark the drag carries. */
	private var dragBase: DragBase? = null

	/** [marked] is the state the press left on the dragged row, which the drag propagates. */
	private class DragBase(val anchor: Int, val rows: List<ItemRow>, val marked: Boolean)

	/** Whether a drag adds its range right now, so a step stays cheap. */
	val dragging: Boolean get() = dragBase != null

	/** Whether a press owns the running gesture, so its drag never turns into a selection. */
	var armed = false
		private set

	/** A gathering press calls it before it toggles, so the gesture stays with the batch. */
	fun begin() {
		armed = true
	}

	private fun set(next: List<ItemRow>) {
		rows.forEach { it.batch(false) }
		rows = next
		markShown(host.models)
		host.batchChanged()
	}

	fun start(picked: List<ItemRow>) {
		armed = true
		set(picked)
	}

	fun clear() = set(emptyList())

	/** Marks the shown rows the gather holds, whichever folder it took them from. */
	fun markShown(shown: List<ItemRow>) {
		val ids = rows.mapTo(HashSet()) { it.item.id }
		shown.forEach { it.batch(it.item.id in ids) }
	}

	/**
	 * The list shows [shown] as the whole view of a container.
	 * - a held container hands its place in the gather to its rows, so the view ticks whole.
	 * - the container leaves the gather, so one untick inside the view keeps the rest held.
	 */
	fun viewShown(shown: List<ItemRow>) {
		takeInView(shown)
		markShown(shown)
	}

	private fun takeInView(shown: List<ItemRow>) {
		if (shown.isEmpty() || !host.clumpsContainer()) return
		val container = host.openContainer() ?: return
		val held = rows.mapTo(HashSet()) { it.item.id }
		if (container.item.id !in held) return
		container.batch(false)
		rows = rows.filterNot { it.item.id == container.item.id } +
			shown.filter { it.item.id !in held }
	}

	/**
	 * The list leaves [left] behind, the whole view of one container.
	 * - a wholly held view collapses into its container, so the parent list ticks it.
	 * - picks under another parent stay, since only the direct view is replaced.
	 * - one unticked row keeps the held rows as the picks and the container unheld.
	 */
	fun viewLeft(left: List<ItemRow>) {
		if (left.isEmpty() || !host.clumpsContainer()) return
		val container = host.openContainer() ?: return
		val held = rows.mapTo(HashSet()) { it.item.id }
		if (!left.all { it.item.id in held }) return
		val leftIds = left.mapTo(HashSet()) { it.item.id }
		val next = rows.filterNot { it.item.id in leftIds && selectedUnder(it, container.item.id) }
		set(if (next.any { it.item.id == container.item.id }) next else next + container)
	}

	/** Whether [row] stands under [containerId], the search path carrying the direct parent last. */
	private fun selectedUnder(row: ItemRow, containerId: Int): Boolean =
		(row.path?.lastOrNull()?.id ?: row.parentId) == containerId

	fun hold(row: ItemRow, on: Boolean) {
		val id = row.item.id
		val held = rows.any { it.item.id == id }
		if (held == on) return
		set(if (on) rows + row else rows.filter { it.item.id != id })
	}

	fun toggle(row: ItemRow) = hold(row, !row.batched)

	/** Takes every shown row, or drops them all once each is in. */
	fun toggleAll() {
		val shown = host.models
		val ids = shown.mapTo(HashSet()) { it.item.id }
		val held = rows.mapTo(HashSet()) { it.item.id }
		set(if (held.containsAll(ids)) rows.filter { it.item.id !in ids }
		else rows + shown.filter { it.item.id !in held })
	}

	/**
	 * One drag step applies the rows [anchor]..[to] to the batch of the drag start.
	 * - pulled-back rows return to that batch.
	 * - the mark the press left on the dragged row decides whether the step marks or clears.
	 */
	fun range(anchor: Int, to: Int) {
		val shown = host.models
		if (shown.isEmpty()) return
		val base = dragBase?.takeIf { it.anchor == anchor } ?: DragBase(
			anchor, rows, shown[anchor.coerceIn(0, shown.lastIndex)].batched,
		).also { dragBase = it }
		val lo = minOf(anchor, to).coerceIn(0, shown.lastIndex)
		val hi = maxOf(anchor, to).coerceIn(0, shown.lastIndex)
		val touched = shown.subList(lo, hi + 1)
		val touchedIds = touched.mapTo(HashSet()) { it.item.id }
		val held = base.rows.mapTo(HashSet()) { it.item.id }
		set(if (base.marked) base.rows + touched.filter { it.item.id !in held }
		else base.rows.filter { it.item.id !in touchedIds })
	}

	fun endRange() {
		armed = false
		dragBase = null
	}
}
