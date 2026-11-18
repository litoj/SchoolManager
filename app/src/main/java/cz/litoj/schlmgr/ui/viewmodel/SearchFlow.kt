package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search flow: query field, live run and back-restore snapshot.
 * - the host owns the list, and this class decides the search effects.
 * - mode writes pass through [Host.setSearchUi].
 * - stacked hop marks live search.
 * - the marks of the submitted list own the run, and the scope holds until the search ends.
 */
internal class SearchFlow(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val history: NavigationHistory,
	private val host: Host,
) {

	interface Host {

		fun clearSelection()

		fun markedRows(): Set<Int>

		val models: List<ItemRow>

		fun appendRows(batch: List<ItemRow>)

		fun removeRows(items: Collection<ItemRow>)

		fun remap()

		fun discardSelection(rows: List<ItemRow>)

		fun load(content: List<ItemRow>, isSearch: Boolean, isPicker: Boolean, anchor: ScrollAnchor? = null)

		fun scrollAnchor(index: Int? = null, offset: Int? = null): ScrollAnchor

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

	internal val run = SearchRun(scope, io, RunHost())

	private var lastSearchRows: List<ItemRow> = emptyList()

	private var lastSearchAnchor: ScrollAnchor? = null

	private var criteria: List<Criterion> = emptyList()

	/**
	 * The rows the session reads, empty for the whole container.
	 * - a run with no new marks keeps it, so a search never widens itself.
	 */
	private var selection: Set<Int> = emptySet()

	var lastSearchInfo: InfoBar? = null

	fun openField() = searchInput.open()

	fun closeField() = searchInput.close()

	fun onTextChange(text: String) = searchInput.onTextChange(text)

	fun onSubmit(text: String) = searchInput.onSubmit(text)

	/**
	 * The walk streams in batches, and a superseded run never publishes.
	 */
	fun submitSearch(query: SearchQuery) {
		// the marks own the scope, and the list loses them with its next content
		val marked = host.markedRows()
		if (marked.isNotEmpty()) selection = marked
		host.clearSelection()
		val path = mutableListOf<DbItem>()
		path.addAll(history.path)
		// a re-run on the same hop must not stack another search entry
		val alreadySearching = history.onSearchHop()
		run.abort()
		// the search reads the toggle per run, so a settings change applies to the next one
		val eng = SearchEngine(query, AppSettings.getBool("searchNoDiacritics", false))
		// a live re-run keeps a single stacked hop.
		if (!alreadySearching) history.hop(null)
		// a new query starts at the top, unlike a history restore.
		host.load(ArrayList(), isSearch = true, isPicker = false, anchor = host.scrollAnchor(0, 0))
		// a new strip starts at zero
		lastSearchInfo = searchInfo(0, done = false)
		host.publishSearchInfoCollapsed(lastSearchInfo)
		run.start(eng, path, selection)
	}

	/** The query a run answers to: the field text and the filters of the dialog. */
	fun currentQuery() = SearchQuery(query.value, criteria)

	/** [criterion] replaces the one of its tag and runs again. */
	fun applyFilter(criterion: Criterion) {
		criteria = currentQuery().with(criterion).criteria
		runFilters()
	}

	/** The filter of [tag] is gone, and the search ends with the last one. */
	fun dropFilter(tag: SearchTag) {
		val rest = currentQuery().without(tag)
		criteria = rest.criteria
		if (rest.isEmpty) endSearch() else runFilters()
	}

	/** Every filter is gone, and the search ends when the field is empty too. */
	fun clearFilters() {
		criteria = emptyList()
		if (query.value.isBlank()) endSearch() else runFilters()
	}

	private fun hasFilters() = criteria.isNotEmpty()

	/** A filter change runs at once, typing pauses belong to the field. */
	private fun runFilters() {
		searchInput.cancelPending()
		submitSearch(currentQuery())
	}

	/** The strip of the live run, its scope naming the rows the run reads. */
	private fun searchInfo(count: Int, done: Boolean) =
		InfoBar.Search(count, criteria, done, selection.size)

	private fun appendSearchRows(batch: List<ItemRow>) {
		host.appendRows(batch)
		lastSearchInfo = searchInfo(host.models.size, done = false)
		host.publishSearchInfo(lastSearchInfo)
	}

	private inner class RunHost : SearchRun.Host {
		override fun appendBatch(batch: List<ItemRow>) = appendSearchRows(batch)

		override fun runFinished() {
			lastSearchInfo = searchInfo(host.models.size, done = true)
			host.publishSearchInfo(lastSearchInfo)
		}
	}

	/**
	 * A false [reload] skips the load, since the caller loads itself.
	 */
	internal fun leaveSearchFlow(reload: Boolean = true) {
		searchInput.cancelPending()
		run.abort()
		lastSearchRows = emptyList()
		lastSearchAnchor = null
		criteria = emptyList()
		selection = emptySet()
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
		lastSearchRows = ArrayList(host.models)
		lastSearchAnchor = host.scrollAnchor()
		host.setSearchUi(SearchUi.OFF)
	}

	/**
	 * Re-shows the last results after a back press onto a stacked run.
	 * @return true when restored, since the caller then skips the browse load
	 */
	fun restoreSearch(): Boolean {
		// the mode decides, not the variant.
		val anchor = lastSearchAnchor ?: return false
		if (host.searchUi() == SearchUi.RESULTS || !history.onSearchHop()) return false
		// a pending browse read must not land over the search strip.
		host.cancelPendingInfoRead()
		host.discardSelection(lastSearchRows)
		host.load(lastSearchRows, isSearch = true, isPicker = host.pickerMode(), anchor = anchor)
		host.setSearchUi(SearchUi.RESULTS)
		host.publishSearchInfoCollapsed(lastSearchInfo)
		return true
	}

	/** The reads run on [io]. */
	fun refreshSearchRows() {
		// snapshot on main, since the ids travel.
		val rows = host.models.toList()
		val ids = rows.map { it.item.id }
		scope.launch {
			val fresh = withContext(io) {
				ids.map { id ->
					val row = ItemRepository.get(id)
					// the flip content of a translation hit is its words
					row to (row?.let(ItemRow::flipContentOf) ?: emptyList())
				}
			}
			// apply on main, since gone rows leave the list.
			val dead = ArrayList<ItemRow>()
			rows.forEachIndexed { i, row ->
				val (f, content) = fresh[i]
				if (f == null) dead.add(row) else row.applyFresh(f, content)
			}
			if (dead.isNotEmpty()) host.removeRows(dead) else host.remap()
			// the count follows the drop.
			host.refreshInfo()
		}
	}

	private inner class SearchInputHost : SearchInput.Host {
		override fun runQuery(text: String) = submitSearch(SearchQuery(text, criteria))
		override fun clearQuery() {
			// a filter alone is still a search, so only an empty query ends it
			when {
				criteria.isNotEmpty() -> submitSearch(SearchQuery("", criteria))
				host.isSearching() -> leaveSearchFlow()
			}
		}

		override fun hasFilters() = this@SearchFlow.hasFilters()

		override fun openField() = host.setSearchUi(SearchUi.TYPING)
		override fun idleField() = host.setSearchUi(SearchUi.RESULTS)

		override fun closeField() =
			host.setSearchUi(
				if (host.searchUi() == SearchUi.RESULTS || (host.isSearching() && history.onSearchHop()))
					SearchUi.RESULTS else SearchUi.OFF)
	}

	/** The keyboard drop, where the query decides whether the field stays. */
	fun dismissField() = searchInput.dismiss()

	/** The nav back out of the search: the results leave and the query resets. */
	fun cancel() {
		searchInput.clear()
		endSearch()
	}
}
