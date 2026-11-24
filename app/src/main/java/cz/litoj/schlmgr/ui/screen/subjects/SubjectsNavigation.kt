package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reference jump and picker landing.
 */
internal class SubjectsNavigation(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val history: NavigationHistory,
	private val host: Host,
	private val graphs: GraphQueries? = null,
) {

	interface Host : SubjectsHost {
		fun selectionCount(): Int

		val pasteArmed: Boolean

		fun openChain(path: MutableList<DbItem>, pulseItemId: Int?)
	}

	/** Opens the reference original: a single source opens, several show the picker. */
	fun onReferenceClick(index: Int) {
		val row = host.items().getOrNull(index) ?: return
		jumpToSources(row.item.id, row.parentId)
	}

	/** Opens the location of the selected hit, which may live in several containers. */
	fun goToSelectedHit(row: ItemRow) {
		// snapshot on main, as are the chain and the search position
		val originPath = history.path.toList()
		val origin = originPath.lastOrNull()
		scope.launch(io) {
			val graphs = checkNotNull(graphs)
			val paths = graphs.parentIds(row.item.id).map { graphs.pathTo(it) }
			if (paths.isEmpty()) return@launch
			host.postToUi {
				// own navigation wins.
				if (history.path.lastOrNull() !== origin) return@postToUi
				if (paths.size == 1) navigateToSource(paths[0], row.item.id)
				else host.emitRequest(Request.PickSource(paths, originPath, row.item.id))
			}
		}
	}

	/** The source jump behind the reference icon, where [exclude] is the own link of the row. */
	private fun jumpToSources(itemId: Int, exclude: Int?) {
		val originPath = history.path.toList()
		val origin = originPath.lastOrNull()
		scope.launch(io) {
			val graphs = checkNotNull(graphs)
			val sources = graphs.parentIds(itemId).filter { it != exclude }
			if (sources.isEmpty()) return@launch
			val paths = sources.map { graphs.pathTo(it) }
			host.postToUi {
				if (history.path.lastOrNull() !== origin) return@postToUi
				if (host.searching || host.selectionCount() > 0 || host.pasteArmed) return@postToUi
				if (paths.size == 1) navigateToSource(paths[0])
				else host.emitRequest(Request.PickSource(paths, originPath, null))
			}
		}
	}

	fun navigateToSource(path: List<DbItem>, pulseItemId: Int? = null) {
		val current = mutableListOf<DbItem>()
		current.addAll(path)
		host.openChain(current, pulseItemId)
	}
}
