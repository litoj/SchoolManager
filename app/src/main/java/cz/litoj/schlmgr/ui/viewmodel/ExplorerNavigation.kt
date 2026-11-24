package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navigation paths and their asynchronous landing guards. */
internal class ExplorerNavigation(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val history: NavigationHistory,
	private val host: Host,
) {
	interface Host {
		fun searching(): Boolean
		fun clearSelection()
		suspend fun orderedChildren(parentId: Int): List<DbItem>
		fun setContent(container: DbItem?)
		fun leaveSearchFlow()
		fun restoreSearch(): Boolean
		fun onChange()
	}

	private var siblingJob: Job? = null
	private var siblingGeneration = 0

	fun invalidate() {
		siblingGeneration++
	}

	fun changedPath(skip: Int) {
		updateBackPath(skip, refreshStrip = false)
		if (host.restoreSearch()) return
		// a closed field has no search mode left, but its producer still needs aborting
		if (host.searching()) host.leaveSearchFlow()
		host.setContent(history.path.lastOrNull())
		host.onChange()
	}

	fun updateBackPath(skip: Int, refreshStrip: Boolean = true) {
		var remaining = skip
		while (remaining > 0) {
			history.remove()
			remaining--
		}
		if (refreshStrip) host.onChange()
	}

	fun openSibling(delta: Int) {
		val path = history.path
		if (path.isEmpty() || host.searching()) return
		host.clearSelection()
		val current = path.last()
		val parent = path.getOrNull(path.size - 2)
		val newPath = ArrayList(path)
		siblingJob?.cancel()
		// the newest landing replaces the whole entry, not just its displayed container
		val generation = ++siblingGeneration
		siblingJob = scope.launch {
			val siblings = withContext(io) { host.orderedChildren(parent?.id ?: ROOT_ID) }
			val index = siblings.indexOfFirst { it.id == current.id }
			if (index < 0) return@launch
			val target = siblings.getOrNull(index + delta) ?: return@launch
			if (target.type != ItemKind.CHAPTER) return@launch
			if (generation != siblingGeneration) return@launch
			if (history.path.lastOrNull() !== current || host.searching()) return@launch
			newPath[newPath.size - 1] = target
			history.replaceCurrent(newPath)
			host.setContent(history.path.lastOrNull())
		}
	}

	fun breadcrumbClick(index: Int) {
		val container = history.path.getOrNull(index) ?: return
		if (history.path.lastOrNull() === container) return
		val path = mutableListOf<DbItem>()
		for (element in history.path) {
			path.add(element)
			if (element === container) break
		}
		if (history.onSearchHop()) host.leaveSearchFlow()
		history.rebaseTo(path)
		host.setContent(container)
		host.onChange()
	}
}
