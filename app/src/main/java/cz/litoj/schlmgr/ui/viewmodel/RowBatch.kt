package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The rows gathered across folders, which every list marks wherever it shows them.
 * - the armed paste and the picker gather through it.
 * - the host owns the shown rows, and this class decides their marks.
 */
internal class RowBatch(private val host: Host) {

	interface Host {

		val models: List<ItemRow>

		/** The batch changed, an emptied one included. */
		fun batchChanged()
	}

	var rows by mutableStateOf<List<ItemRow>>(emptyList())
		private set

	/** The batch at the start of the running drag, so pulled-back rows return to it. */
	private var dragBase: Pair<Int, List<ItemRow>>? = null

	/** Whether a drag adds its range right now, so a step stays cheap. */
	val dragging: Boolean get() = dragBase != null

	private fun set(next: List<ItemRow>) {
		rows.forEach { it.batch(false) }
		rows = next
		markShown(host.models)
		host.batchChanged()
	}

	fun start(picked: List<ItemRow>) = set(picked)

	fun clear() = set(emptyList())

	/** Marks the shown rows the batch holds, whichever folder it took them from. */
	fun markShown(shown: List<ItemRow>) {
		val ids = rows.mapTo(HashSet()) { it.item.id }
		shown.forEach { it.batch(it.item.id in ids) }
	}

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
	 * One drag step adds the rows [anchor]..[to] to the batch of the drag start.
	 * - pulled-back rows return to that batch.
	 */
	fun range(anchor: Int, to: Int) {
		val shown = host.models
		if (shown.isEmpty()) return
		val base = dragBase?.takeIf { it.first == anchor }?.second
			?: rows.also { dragBase = anchor to it }
		val lo = minOf(anchor, to).coerceIn(0, shown.lastIndex)
		val hi = maxOf(anchor, to).coerceIn(0, shown.lastIndex)
		val held = base.mapTo(HashSet()) { it.item.id }
		set(base + shown.subList(lo, hi + 1).filter { it.item.id !in held })
	}

	fun endRange() {
		dragBase = null
	}
}
