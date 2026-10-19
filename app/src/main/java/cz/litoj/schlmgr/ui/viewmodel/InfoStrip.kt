package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.ItemRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Info strip machine — browse read, search count refresh, toggle.
 * - host owns state — this class decides strip content.
 */
internal class InfoStrip(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {

		val path: List<DbItem>

		fun listSize(): Int

		fun isSearching(): Boolean

		fun loadPending(): Boolean

		var lastSearchInfo: InfoBar?

		fun publishInfo(info: InfoBar)

		fun publishInfoAndCrumbs(info: InfoBar)

		fun publishCrumbs()

		fun toggleExpanded()
	}

	private var infoJob: Job? = null

	/**
	 * Refreshes strip — browse re-reads stats, search only count.
	 * - shown results keep search strip.
	 */
	fun refreshInfo() {
		if (host.isSearching()) {
			val info = (host.lastSearchInfo as? InfoBar.Search)?.copy(count = host.listSize())
				?: InfoBar.Search(host.listSize(), 0)
			host.lastSearchInfo = info
			host.publishInfo(info)
			return
		}
		setInfo(host.path.lastOrNull())
	}

	/**
	 * Rebuilds strip from list size and container stats.
	 * - runs on [io] — a newer refresh cancels the stale one.
	 * - pending load defers strip.
	 */
	fun setInfo(container: DbItem?) {
		infoJob?.cancel()
		if (host.loadPending()) {
			host.publishCrumbs()
			return
		}
		// Count reads on main.
		val count = host.listSize()
		infoJob = scope.launch {
			val info = withContext(io) { infoBarOf(container, count) }
			host.publishInfoAndCrumbs(info)
		}
	}

	private fun infoBarOf(container: DbItem?, count: Int): InfoBar {
		if (container != null) {
			val counts = ItemRepository.recomputeCounters(container.id)
			val ratio = if (counts.passed + counts.failed == 0) -1 else 100 * counts.passed / (counts.passed + counts.failed)
			return InfoBar.Children(count, ratio, container.description)
		}
		return InfoBar.Root(count)
	}

	fun toggleExpanded() {
		host.toggleExpanded()
	}

	fun cancelPendingRead() {
		infoJob?.cancel()
	}
}
