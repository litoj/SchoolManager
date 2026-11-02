package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.ItemKind

/**
 * The remembered view state of a container, its scroll and the flip sides of its rows.
 * - captured when a load leaves the view, applied when the view returns.
 * - lives with the view model, so a rotation keeps it and a restart drops it.
 */
internal class ViewStateStore {

	class ViewState(val scrollIndex: Int, val scrollOffset: Int, val flips: Map<Int, Boolean>)

	private val states = HashMap<Int, ViewState>()

	/**
	 * Saves the state of the view the list is leaving.
	 * - search rows are not the view of the container, so the caller skips the capture.
	 */
	fun capture(containerId: Int, scrollIndex: Int, scrollOffset: Int, rows: List<ItemRow>) {
		val flips = HashMap<Int, Boolean>()
		for (row in rows) {
			val type = row.item.type
			if (type == ItemKind.WORD || type == ItemKind.TRANSLATION) flips[row.item.id] = row.flipped
		}
		states[containerId] = ViewState(scrollIndex, scrollOffset, flips)
	}

	/** The remembered state of a container, or null on a first visit. */
	fun stateOf(containerId: Int): ViewState? = states[containerId]

	/**
	 * Applies the remembered flip sides onto the fresh rows of the container.
	 * - a row without a memory keeps its default side.
	 */
	fun applyFlips(containerId: Int, rows: List<ItemRow>) {
		val flips = states[containerId]?.flips ?: return
		for (row in rows) flips[row.item.id]?.let { row.flipOnto(it) }
	}
}
