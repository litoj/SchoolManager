package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.search.SearchEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search flow — query field, live run, back-restore snapshot.
 * - host owns list — this class decides search effects.
 * - mode writes pass through [Host.setSearchUi].
 * - stacked hop marks live search.
 */
internal class SearchFlow(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val history: NavigationHistory,
	private val host: Host,
	private val clock: () -> Long = System::currentTimeMillis,
) {

	interface Host {

		fun clearSelection()

		val models: List<ItemRow>

		fun appendRows(batch: List<ItemRow>)

		fun removeRows(items: Collection<ItemRow>)

		fun remap()

		fun load(content: List<ItemRow>, isSearch: Boolean, isPicker: Boolean)

		fun isSearching(): Boolean

		fun pickerMode(): Boolean

		fun searchUi(): SearchUi

		fun setSearchUi(mode: SearchUi)

		fun publishSearchInfo(info: InfoBar?)

		fun publishSearchInfoCollapsed(info: InfoBar?)

		fun refreshInfo()

		fun cancelPendingInfoRead()

		fun setContent(container: DbItem?)
	}

	private val searchInput = SearchInput(scope, SearchInputHost())

	val query: StateFlow<String> get() = searchInput.query

	internal val run = SearchRun(scope, io, RunHost(), clock = clock)

	private var lastSearchRows: List<ItemRow> = emptyList()

	var lastSearchInfo: InfoBar? = null

	fun openField() = searchInput.open()

	fun closeField() = searchInput.close()

	fun onTextChange(text: String) = searchInput.onTextChange(text)

	fun onSubmit(text: String) = searchInput.onSubmit(text)

	internal fun snapshotRestoreRows() {
		lastSearchRows = ArrayList(host.models)
	}

	/**
	 * Searches open container over preloaded index.
	 * - walk streams in batches — a superseded run never publishes.
	 */
	fun submitSearch(q: String) {
		host.clearSelection()
		val path = mutableListOf<DbItem>()
		path.addAll(history.path)
		// Stacked hop decides.
		val alreadySearching = history.onSearchHop()
		run.abort()
		val eng = SearchEngine(q)
		if (!eng.valid) {
			// Bad regex — the error shows.
			val detail = eng.regexError
			if (detail != null) Startup.showNotice(UiNotice.BadPattern(detail))
			return
		}
		// Live re-run keeps single stacked hop.
		if (!alreadySearching) history.hop(null)
		// Start empty — batches fill it.
		host.load(ArrayList(), isSearch = true, isPicker = false)
		// New strip starts at zero.
		lastSearchInfo = InfoBar.Search(0, 0)
		host.publishSearchInfoCollapsed(lastSearchInfo)
		run.start(eng, path)
	}

	private fun appendSearchRows(batch: List<ItemRow>) {
		host.appendRows(batch)
		lastSearchRows = ArrayList(host.models)
		lastSearchInfo = InfoBar.Search(host.models.size, run.elapsed())
		host.publishSearchInfo(lastSearchInfo)
	}

	private inner class RunHost : SearchRun.Host {
		override fun appendBatch(batch: List<ItemRow>) = appendSearchRows(batch)

		override fun runFinished() {
			lastSearchInfo = InfoBar.Search(host.models.size, run.elapsed())
			host.publishSearchInfo(lastSearchInfo)
		}
	}

	/**
	 * Leaves flow — aborts run, unwinds hops, restores browse list.
	 * - [reload] off skips load — the caller loads itself.
	 */
	internal fun leaveSearchFlow(reload: Boolean = true) {
		searchInput.cancelPending()
		run.abort()
		lastSearchRows = emptyList()
		history.removeSearchHops()
		if (reload) host.setContent(history.path.lastOrNull())
	}

	fun endSearch() {
		leaveSearchFlow()
		host.setSearchUi(SearchUi.OFF)
		host.refreshInfo()
	}

	fun suspendSearch() {
		searchInput.cancelPending()
		run.abort()
		host.setSearchUi(SearchUi.OFF)
	}

	/**
	 * Re-shows last results after back onto stacked run.
	 * @return true when restored — the caller skips the browse load.
	 */
	fun restoreSearch(): Boolean {
		// Mode decides, not variant.
		if (host.searchUi() == SearchUi.RESULTS || lastSearchRows.isEmpty() || !history.onSearchHop()) return false
		// Pending browse read must not land over search strip.
		host.cancelPendingInfoRead()
		lastSearchRows.forEach { it.selected = false }
		host.load(lastSearchRows, isSearch = true, isPicker = host.pickerMode())
		host.setSearchUi(SearchUi.RESULTS)
		host.publishSearchInfoCollapsed(lastSearchInfo)
		return true
	}

	/**
	 * Re-reads shown search rows in place after mutation.
	 * - reads run on [io].
	 */
	fun refreshSearchRows() {
		// Snapshot on main — the ids travel.
		val rows = host.models.toList()
		val ids = rows.map { it.item.id }
		scope.launch {
			val fresh = withContext(io) {
				ids.map { ItemRepository.get(it) to ItemRepository.translations(it) }
			}
			// Apply on main — gone rows leave the list.
			val dead = ArrayList<ItemRow>()
			rows.forEachIndexed { i, row ->
				val f = fresh[i].first
				if (f == null) dead.add(row) else row.applyFresh(f, fresh[i].second)
			}
			if (dead.isNotEmpty()) host.removeRows(dead) else host.remap()
			// Count follows drop.
			host.refreshInfo()
		}
	}

	private inner class SearchInputHost : SearchInput.Host {
		override fun runQuery(text: String) = submitSearch(text)
		override fun clearQuery() {
			if (host.isSearching()) leaveSearchFlow()
		}

		override fun supersedeRun() = run.abort()
		override fun openField() = host.setSearchUi(SearchUi.TYPING)

		override fun closeField() =
			host.setSearchUi(
				if (host.searchUi() == SearchUi.RESULTS || (host.isSearching() && history.onSearchHop()))
					SearchUi.RESULTS else SearchUi.OFF)
	}
}
