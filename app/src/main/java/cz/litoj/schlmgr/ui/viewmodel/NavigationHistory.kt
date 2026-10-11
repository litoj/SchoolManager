package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem

val mainHistory = NavigationHistory()

/**
 * The navigation history of the explorer list.
 * - [path]: the displayed container chain (root first, open container last).
 * - [onePath]: how many hops the current entry stacks — descent and
 *   search alike.
 * - a fresh instance is a valid empty position ([onePath] holds its base 0).
 */
class NavigationHistory {
	var path: MutableList<DbItem> = mutableListOf()
		private set
	private val prevPaths = mutableListOf<MutableList<DbItem>>()

	val onePath = mutableListOf(0)

	/**
	 * Kinds of the stacked same-path hops, oldest first.
	 * - `true` = a descent (grew [path] by one).
	 * - `false` = a search run (left [path] alone).
	 */
	private val hopDescents = mutableListOf<Boolean>()

	fun push(newPath: MutableList<DbItem>) {
		prevPaths.add(path)
		path = newPath
		onePath.add(0)
	}

	/**
	 * Extends the current entry: [item] descends one level, `null` stacks
	 * a search hop.
	 */
	fun hop(item: DbItem?) {
		if (item != null) path.add(item)
		hopDescents.add(item != null)
		onePath[onePath.lastIndex]++
	}

	/**
	 * Replaces the current entry's path without pushing a history entry —
	 * a sibling switch (the explorer's horizontal swipe) is the same step
	 * with its endpoint moved, so a back press pops the whole entry to the
	 * previous path (a level up), not to the pre-switch sibling.
	 */
	fun replaceCurrent(newPath: MutableList<DbItem>) {
		path = newPath
	}

	/**
	 * Pops one navigation step.
	 * - a descent hop unwinds one path element — a search hop leaves the
	 *   path alone.
	 *
	 * @return `true` when a hop was popped (a search hop leaves the caller
	 *   in place, while a descent hop goes one level up — the caller reloads),
	 *   `false` when an entry boundary was crossed
	 */
	fun remove(): Boolean {
		var ret: Boolean
		if (onePath.last() > 0) {
			onePath[onePath.lastIndex]--
			// `removeAt(lastIndex)`, not `removeLast()`: the latter binds to the
			// JDK 21 `java.util.List.removeLast`, which no Android version below
			// 35 has
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
	 * Pops the search hops stacked on top (they added no path element), stopping
	 * at the first descent hop. The path is left unchanged.
	 */
	fun removeSearchHops() {
		while (onePath.last() > 0 && hopDescents.lastOrNull() == false) remove()
	}

	/**
	 * Whether the position the last [remove] landed on is a stacked search run —
	 * i.e. the screen being returned to was the search-result list, so the screen
	 * restores it instead of loading the container's browse contents.
	 */
	fun onSearchHop(): Boolean =
		onePath.last() > 0 && hopDescents.lastOrNull() == false
}
