package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem

val mainHistory = NavigationHistory()

/**
 * The navigation history of the explorer list.
 * - [path] holds the displayed container chain, root first and open container last.
 * - [onePath] counts the hops the current entry stacks, descent and search alike.
 * - a fresh instance is a valid empty position, where [onePath] holds its base 0.
 */
class NavigationHistory {
	var path: MutableList<DbItem> = mutableListOf()
		private set
	private val prevPaths = mutableListOf<MutableList<DbItem>>()

	val onePath = mutableListOf(0)

	/**
	 * Kinds of the stacked same-path hops, oldest first.
	 * - true means a descent, which grew [path] by one.
	 * - false means a search run, which left [path] alone.
	 */
	private val hopDescents = mutableListOf<Boolean>()

	fun push(newPath: MutableList<DbItem>) {
		prevPaths.add(path)
		path = newPath
		onePath.add(0)
	}

	/**
	 * Extends the current entry: [item] descends one level, and null stacks a search hop.
	 */
	fun hop(item: DbItem?) {
		if (item != null) path.add(item)
		hopDescents.add(item != null)
		onePath[onePath.lastIndex]++
	}

	/**
	 * Replaces the path of the current entry without pushing a history entry.
	 * - a sibling switch, the horizontal swipe of the explorer, is the same step.
	 * - its endpoint moves, so a back press pops the whole entry to the previous path.
	 * - that is a level up, not the pre-switch sibling.
	 */
	fun replaceCurrent(newPath: MutableList<DbItem>) {
		path = newPath
	}

	/**
	 * Pops one navigation step.
	 * - a descent hop unwinds one path element, and a search hop leaves the path alone.
	 *
	 * @return true when a hop was popped, or false when an entry boundary was crossed
	 * - a search hop leaves the caller in place.
	 * - a descent hop goes one level up, so the caller reloads.
	 */
	fun remove(): Boolean {
		var ret: Boolean
		if (onePath.last() > 0) {
			onePath[onePath.lastIndex]--
			// removeAt on the last index, not removeLast: the latter binds to the JDK 21
			// java.util.List.removeLast, which no Android version below 35 has
			if (hopDescents.removeAt(hopDescents.lastIndex)) path.removeAt(path.lastIndex)
			ret = true
		} else {
			onePath.removeAt(onePath.lastIndex)
			if (onePath.isEmpty()) {
				onePath.add(0)
				path = mutableListOf()
			} else {
				path = prevPaths.removeAt(prevPaths.lastIndex)
			}
			ret = false
		}
		return ret
	}

	/**
	 * Pops the search hops stacked on top, which added no path element.
	 * - it stops at the first descent hop, and the path stays unchanged.
	 */
	fun removeSearchHops() {
		while (onePath.last() > 0 && hopDescents.lastOrNull() == false) remove()
	}

	/**
	 * Whether the position the last [remove] landed on is a stacked search run.
	 * - that means the screen being returned to was the search-result list.
	 * - it then restores it instead of loading the browse contents of the container.
	 */
	fun onSearchHop(): Boolean =
		onePath.last() > 0 && hopDescents.lastOrNull() == false
}
