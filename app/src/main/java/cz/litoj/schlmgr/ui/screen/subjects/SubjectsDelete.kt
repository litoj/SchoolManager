package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The deletion of the subjects screen.
 * - it covers the confirm request and the region-aware removal.
 */
internal class SubjectsDelete(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {
		fun items(): List<ItemRow>

		val searching: Boolean

		/** The open chain, where the searched region sits in the last entry. */
		val path: List<DbItem>

		fun emitRequest(request: Request)

		fun postToUi(work: Runnable)

		fun removeItems(items: Collection<ItemRow>)

		fun refreshSearchRows()

		fun refreshInfo()
	}

	fun onDeleteAction() {
		val doomed = host.items().filter(ItemRow::selected)
		if (doomed.isEmpty()) return
		host.emitRequest(Request.ConfirmDelete(doomed))
	}

	/**
	 * A search at the subject level clears every occurrence in the subject.
	 * - a deeper search clears only the occurrences inside the subtree of that chapter.
	 * - occurrences outside the region survive, so a hit may outlive its deletion.
	 * - it then stays in the results.
	 */
	fun performDelete(doomed: List<ItemRow>, title: String? = null) {
		val searchNow = host.searching
		// snapshot on main, since the io pass must not read the live rows
		val regionRoot = if (searchNow) host.path.lastOrNull() else null
		val marks = doomed.map { it.item.id to it.parentId }
		scope.launch(io) {
			val links = if (regionRoot == null) marks
			else {
				// every parent inside the searched region: one subgraph read.
				// then the own parent links of the doomed rows
				val region = ItemRepository.subgraph(regionRoot.id).mapTo(HashSet()) { it.id }
				marks.flatMap { (id, _) ->
					ItemRepository.parentIds(id).filter { it in region }.map { id to it }
				}
			}
			if (title == null) for ((id, parent) in links) ItemRepository.remove(id, parent)
			else FileProgress(title).use { progress ->
				// the count is unknown before the walk, so the bar shows the running count.
				progress.rows(0)
				for ((id, parent) in links) ItemRepository.remove(id, parent, progress)
			}
			host.postToUi {
				// a search re-reads, since the region deletion takes more than the selection.
				if (searchNow) host.refreshSearchRows() else host.removeItems(doomed)
				host.refreshInfo()
			}
		}
	}
}
