package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reference jump, sibling swipe, picker landing.
 */
internal class SubjectsNavigation(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val history: NavigationHistory,
	private val host: Host,
) {

	interface Host {
		val searching: Boolean

		fun selectionCount(): Int

		val pasteArmed: Boolean

		fun postToUi(work: Runnable)

		fun emitRequest(request: Request)

		fun setContent(container: DbItem?)

		fun openChain(path: MutableList<DbItem>)

		fun switchedSibling(step: Int, delta: Int)

		/** Drops the row selection, while an armed move stays. */
		fun clearSelection()
	}

	/** Opens the reference original: a single source opens, several show the picker. */
	fun onReferenceClick(ui: ItemUiModel) {
		val row = ui.payload ?: return
		jumpToSources(row.item.id, row.parentId)
	}

	/** Opens the location of the selected hit, which may live in several containers. */
	fun goToSelectedHit(row: ItemRow) {
		// snapshot on main, as are the chain and the search position
		val originPath = history.path.toList()
		val origin = originPath.lastOrNull()
		scope.launch(io) {
			val paths = ItemRepository.parentIds(row.item.id).map { ItemRepository.pathTo(it) }
			if (paths.isEmpty()) return@launch
			host.postToUi {
				// own navigation wins.
				if (history.path.lastOrNull() !== origin) return@postToUi
				if (paths.size == 1) navigateToSource(paths[0])
				else host.emitRequest(Request.PickSource(paths, originPath))
			}
		}
	}

	/** The source jump behind the reference icon, where [exclude] is the own link of the row. */
	private fun jumpToSources(itemId: Int, exclude: Int?) {
		val originPath = history.path.toList()
		val origin = originPath.lastOrNull()
		scope.launch(io) {
			val sources = ItemRepository.parentIds(itemId).filter { it != exclude }
			if (sources.isEmpty()) return@launch
			val paths = sources.map { ItemRepository.pathTo(it) }
			host.postToUi {
				if (history.path.lastOrNull() !== origin) return@postToUi
				if (host.searching || host.selectionCount() > 0 || host.pasteArmed) return@postToUi
				if (paths.size == 1) navigateToSource(paths[0])
				else host.emitRequest(Request.PickSource(paths, originPath))
			}
		}
	}

	/**
	 * Opens the sibling delta away.
	 * - it replaces the entry, so back goes up.
	 * - the swipe cancels the row selection.
	 * - an armed move stays alive, and its paste can land on the switched chapter.
	 */
	fun openSibling(delta: Int) {
		val path = history.path
		if (path.isEmpty() || host.searching) return
		host.clearSelection()
		val current = path.last()
		val parent = path.getOrNull(path.size - 2)
		val pathCopy = ArrayList(path)
		siblingJob?.cancel()
		// a stale swipe must not land over a newer one.
		val generation = ++siblingGeneration
		siblingJob = scope.launch(io) {
			val siblings = if (parent != null) ItemRepository.children(parent.id) else ItemRepository.subjects()
			val index = siblings.indexOfFirst { it.id == current.id }
			if (index < 0) return@launch
			val target = siblings.getOrNull(index + delta) ?: return@launch
			// only chapters can open.
			if (target.type != ItemKind.CHAPTER) return@launch
			val newPath = pathCopy
			newPath[newPath.size - 1] = target
			host.postToUi {
				// a newer navigation supersedes.
				if (generation != siblingGeneration) return@postToUi
				if (history.path.lastOrNull() !== current) return@postToUi
				// a mid-read search blocks the landing, since the selection already left.
				// an armed move never left
				if (host.searching) return@postToUi
				history.replaceCurrent(newPath)
				siblingStep++
				host.switchedSibling(siblingStep, delta)
				host.setContent(history.path.lastOrNull())
			}
		}
	}

	private var siblingStep = 0

	private var siblingJob: Job? = null

	private var siblingGeneration = 0

	fun navigateToSource(path: List<DbItem>) {
		val current = mutableListOf<DbItem>()
		current.addAll(path)
		host.openChain(current)
	}
}
