package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.ItemKind

/**
 * The remembered view state of the current level and its parents.
 * - the level map holds every sibling visited under the current parent.
 * - the stack holds one state per ancestor on the open path.
 * - a descent clears the level, an ascent carries the target parent back into it.
 * - lives with the view model, so a rotation keeps it and a restart drops it.
 */
internal class ViewStateStore {

	class ViewState(
		val scrollIndex: Int,
		val scrollOffset: Int,
		val flips: Map<Int, Boolean>,
		val order: List<Int>? = null,
	)

	private val parents = ArrayDeque<ViewState?>()

	private val level = HashMap<Int, ViewState>()

	private var depth = 0

	private var pathIds: List<Int> = emptyList()

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
		level[containerId] = ViewState(scrollIndex, scrollOffset, flips, rows.map { it.item.id })
	}

	/**
	 * Moves the memory from [fromId] to [containerId] at [pathIds], the full open path.
	 * - a descent pushes the current state and starts a fresh level.
	 * - an ascent drops the level and carries the target parent state back into it.
	 * - a sibling keeps the level, and another branch at the same depth resets it.
	 * @return the remembered state of [containerId], null on a first visit
	 */
	fun moveTo(fromId: Int, containerId: Int, pathIds: List<Int>): ViewState? {
		val targetDepth = pathIds.size
		when {
			targetDepth > depth -> {
				parents.addLast(level[fromId])
				// a jump can skip levels, so the missing parents stay unknown
				while (parents.size < targetDepth) parents.addLast(null)
				level.clear()
			}
			targetDepth < depth -> {
				level.clear()
				var carried: ViewState? = null
				while (parents.size > targetDepth) carried = parents.removeLast()
				if (carried != null) level[containerId] = carried
			}
			pathIds.dropLast(1) != this.pathIds.dropLast(1) -> {
				level.clear()
				parents.clear()
				repeat(targetDepth) { parents.addLast(null) }
			}
		}
		depth = targetDepth
		this.pathIds = pathIds
		return level[containerId]
	}

	/** The remembered sibling order of a container, null when no order was captured yet. */
	fun orderOf(containerId: Int): List<Int>? = level[containerId]?.order

	/** Clears only the sort memory of a container, so scroll and flip sides survive. */
	fun clearOrder(containerId: Int) {
		val state = level[containerId] ?: return
		level[containerId] = ViewState(state.scrollIndex, state.scrollOffset, state.flips)
	}

	/**
	 * Applies the remembered flip sides onto the fresh rows of the container.
	 * - a row without a memory keeps its default side.
	 */
	fun applyFlips(containerId: Int, rows: List<ItemRow>) {
		val flips = level[containerId]?.flips ?: return
		for (row in rows) flips[row.item.id]?.let { row.flipOnto(it) }
	}

	/**
	 * Restores the displayed sibling order of a container.
	 * - new rows append after the remembered ones, instead of replacing that order.
	 */
	fun applyOrder(containerId: Int, rows: List<ItemRow>): List<ItemRow> {
		val order = orderOf(containerId) ?: return rows
		val byId = rows.associateBy { it.item.id }
		val ordered = order.mapNotNullTo(mutableListOf()) { byId[it] }
		val known = order.toSet()
		ordered += rows.filter { it.item.id !in known }
		for ((index, row) in ordered.withIndex()) row.renumber(index + 1)
		return ordered
	}
}
