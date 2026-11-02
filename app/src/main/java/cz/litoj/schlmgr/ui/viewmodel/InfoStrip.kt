package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The info strip machine: browse read, search count refresh and toggle.
 * - the host owns the state, and this class decides the strip content.
 */
internal class InfoStrip(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {

		val path: List<DbItem>

		fun rows(): List<ItemRow>

		fun isSearching(): Boolean

		fun loadPending(): Boolean

		var lastSearchInfo: InfoBar?

		fun publishInfo(info: InfoBar)

		fun publishInfoAndCrumbs(info: InfoBar)

		fun publishCrumbs()

		fun toggleExpanded()
	}

	private var infoJob: Job? = null

	/** The shown results keep the search strip. */
	fun refreshInfo() {
		if (host.isSearching()) {
			val count = host.rows().size
			val info = when (val last = host.lastSearchInfo) {
				is InfoBar.Search -> last.copy(count = count)
				InfoBar.InvalidSearch, InfoBar.InvalidPattern -> last
				else -> InfoBar.Search(count, 0)
			}
			host.lastSearchInfo = info
			host.publishInfo(info)
			return
		}
		setInfo(host.path.lastOrNull())
	}

	/**
	 * Rebuilds the strip from the row kinds and the container stats.
	 * - it runs on [io], and a newer refresh cancels the stale one.
	 * - a pending load defers the strip.
	 */
	fun setInfo(container: DbItem?) {
		infoJob?.cancel()
		if (host.loadPending()) {
			host.publishCrumbs()
			return
		}
		// the counts read on main.
		val kinds = host.rows().groupingBy { it.item.type }.eachCount()
		infoJob = scope.launch {
			val info = withContext(io) { infoBarOf(container, kinds) }
			host.publishInfoAndCrumbs(info)
		}
	}

	private fun infoBarOf(container: DbItem?, kinds: Map<ItemKind, Int>): InfoBar {
		if (container != null) {
			val counts = ItemRepository.recomputeCounters(container.id)
			val ratio = if (counts.passed + counts.failed == 0) -1 else 100 * counts.passed / (counts.passed + counts.failed)
			return InfoBar.Children(kinds, ratio, container.description)
		}
		return InfoBar.Root(kinds.values.sum())
	}

	fun toggleExpanded() {
		host.toggleExpanded()
	}

	fun cancelPendingRead() {
		infoJob?.cancel()
	}
}
