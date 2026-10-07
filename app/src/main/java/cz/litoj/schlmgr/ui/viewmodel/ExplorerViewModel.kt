package cz.litoj.schlmgr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.ui.component.ExplorerRowVariant
import cz.litoj.schlmgr.ui.component.ItemListState
import cz.litoj.schlmgr.ui.component.toUi
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchIndex
import cz.litoj.schlmgr.ui.viewmodel.CurrentData.BackLog
import cz.litoj.schlmgr.ui.viewmodel.CurrentData.EasyList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The immutable screen state the Compose scaffold renders. Everything the legacy
 * `ExplorerStuff` read out of live Views (`path` buttons, the info strip's text and
 * collapsed/expanded height) or out of `VS` is captured here. The search input
 * itself ([query] + [searchFieldOpen]) is separate state below, owned here too —
 * any host renders the shared field from it. The bottom strip is derived from
 * the list state by the host.
 */
data class ExplorerScreenState(
	/** Breadcrumb labels, one per `BackLog.path` entry, in order. */
	val breadcrumbs: List<String> = emptyList(),
	/** The info-strip text ([R.string.data_children_sf] / [R.string.data_child_count]). */
	val infoText: String = "",
	/** Whether the expandable info strip currently shows its full height. */
	val isInfoExpanded: Boolean = false,
	/** `BackLog.path` is non-empty (we're inside a container, not at subjects root). */
	val insideContainer: Boolean = false,
	/** The host can reorder the list (a container's browse content, not a search/picker). */
	val reorderable: Boolean = false,
)

/**
 * The explorer's single state holder — the merge of the former `ExplorerController`
 * (the Compose-side counterpart of the deleted `ExplorerStuff`: breadcrumbs, the
 * info strip, search runs, `BackLog` wiring) and the former `ItemListViewModel`
 * (the row models, selection, flips, reorder). One screen has one state holder:
 * the fragments are views, the user's actions go fragment → this view model, and
 * it holds the state and delegates the work to [ItemRepository].
 *
 * The two browsing behaviours that the legacy code coupled to its hosts stay
 * injected, now as an attachable contract: [attachHost]/[detachHost] follow the
 * fragment's view lifecycle, so a detached view is never called into. Anything a
 * host must do (content switching, menu/title work, selection-bar bookkeeping)
 * still arrives through [Host]; everything else the view model does itself.
 *
 * @param backLog the navigation backlog this screen reads and writes (the main
 *   explorer's global one, the picker's own).
 * @param picker `true` for the SelectItemsFragment host: every row shows its checkbox.
 */
class ExplorerViewModel(
	val backLog: BackLog,
	private val picker: Boolean = false,
) : ViewModel() {

	/**
	 * The host-injected behaviours the legacy `ExplorerStuff` delegated to
	 * `MainFragment`/`SelectItemsFragment` through its `Content`/listener callbacks.
	 */
	interface Host {
		/**
		 * Legacy `Content.setContent` — the host loads [bd]'s children into the
		 * view model ([load]) and does its container-specific menu/title/search work.
		 */
		fun setContent(bd: DbItem?, parent: DbItem?, pathLength: Int)

		/** The host's selection/paste strip state changed (legacy `setSelectOpts`/`setVisibleOpts`). */
		fun onSelectionChanged()

		/** A search run started or ended — the host clears its selection. */
		fun onSearchStateChanged()

		/** Legacy `BackUpdater.changedPath` — the host restores [skip] screens back. */
		fun changedPath(skip: Int)
	}

	/** Builds view models carrying their screen's backlog (`by viewModels { Factory(...) }`). */
	class Factory(private val backLog: BackLog, private val picker: Boolean = false) :
		ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T =
			ExplorerViewModel(backLog, picker) as T
	}

	/** The attached view, if the fragment's view currently exists. */
	private var host: Host? = null

	/** Starts delivering [Host] callbacks to [h] (the fragment's `onViewCreated`). */
	fun attachHost(h: Host) {
		host = h
	}

	/** Stops delivering [Host] callbacks (the fragment's `onDestroyView`). */
	fun detachHost() {
		host = null
	}

	private val _state = MutableStateFlow(ExplorerScreenState())
	val state: StateFlow<ExplorerScreenState> = _state.asStateFlow()

	private val _listState = MutableStateFlow(ItemListState())

	/** The displayed item list — browse content and search results alike. */
	val listState: StateFlow<ItemListState> = _listState.asStateFlow()

	/** `true` while the current list is a search-result list (`SearchAdapter.search`). */
	var search = false
		private set

	/** How one live input state routes: run, clear, or keep the shown list. */
	enum class LiveSearchAction { RUN, CLEAR, IGNORE }

	companion object {
		/** Typed text shorter than this never starts a run on its own. */
		const val SEARCH_MIN_CHARS = 3

		/** Quiet time after the last keystroke before a live run starts. */
		const val SEARCH_DEBOUNCE_MS = 300L

		/**
		 * How many streamed search rows one publish carries: small enough that
		 * the first rows appear while the walk still runs, large enough that a
		 * few-thousand-hit run does not publish per row.
		 */
		const val SEARCH_BATCH = 32

		/**
		 * Routes one live input state: empty clears the run, short text keeps
		 * the shown list, longer text runs. Pure policy, no run of its own —
		 * an explicit submit ([onQuerySubmit]) always runs non-empty text.
		 */
		fun classifyLiveInput(text: String): LiveSearchAction = when {
			text.isEmpty() -> LiveSearchAction.CLEAR
			text.length < SEARCH_MIN_CHARS -> LiveSearchAction.IGNORE
			else -> LiveSearchAction.RUN
		}
	}

	/** The search field text, mirrored from the shared field. */
	private val _query = MutableStateFlow("")
	val query: StateFlow<String> = _query.asStateFlow()

	/** Whether the search field is open. Closing it keeps the results. */
	private val _searchFieldOpen = MutableStateFlow(false)
	val searchFieldOpen: StateFlow<Boolean> = _searchFieldOpen.asStateFlow()

	/** The pending debounced live run, if keystrokes are still arriving. */
	private var searchInputJob: Job? = null

	/** Whether the host is the picker (checkboxes always on); set once by [load]. */
	private var pickerMode = false

	/** One run's dedup/visited sets and its abort flag. */
	private var engine: SearchEngine? = null
	/** The live search run; superseding or leaving it cancels the publish. */
	private var searchJob: Job? = null
	private var searchStart = 0L

	/**
	 * The last completed search's rows and info-strip text, kept so a back hop that
	 * lands on the stacked search run can restore the filtered list instead of the
	 * container's full contents (see [restoreSearch]).
	 */
	private var lastSearchRows: List<ItemRow> = emptyList()
	private var lastSearchInfoText = ""

	/**
	 * Currently loaded row models, parallel to [ItemListState.items]
	 * ([load]'s mapping keeps the source model as the UI item's
	 * [cz.litoj.schlmgr.ui.component.ItemUiModel.key]).
	 */
	private var models = mutableListOf<ItemRow>()

	// ------------------------------------------------------------------ list loading

	/**
	 * Legacy `setContent`: replaces the displayed items. [content] is the converted
	 * [ItemRow] list (`ItemRow.convert(children, bd)`), [isSearch] marks a
	 * search-result list, [isPicker] fixes picker mode for
	 * the SelectItemsFragment host (see also the [picker] constructor flag).
	 *
	 * A browse list loads whole; a search run starts here with an empty list
	 * and streams its batches in afterwards ([appendSearchRows]). Every load
	 * publishes the new state: unlike the legacy adapter swap there is no
	 * separate refresh call.
	 */
	fun load(content: List<ItemRow>, isSearch: Boolean = false, isPicker: Boolean = false) {
		search = isSearch
		pickerMode = isPicker || picker
		models = content.toMutableList()
		_listState.update {
			it.copy(
				items = models.map { row -> row.toUi() },
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				variant = if (isSearch) ExplorerRowVariant.Search else ExplorerRowVariant.Browse,
				pickerMode = pickerMode,
				showDesc = ItemRow.showDesc,
				// fresh content carries no selection; the picker always selects
				selectionActive = selectionActive(pickerMode),
			)
		}
		_state.update {
			it.copy(
				// every content switch re-reads the path — the crumbs must follow each
				// open/back, not only the explicit onChange/setInfo refreshes
				breadcrumbs = backLog.path.map { container -> container.name },
				insideContainer = backLog.path.isNotEmpty(),
				// a search list or the picker has no persistent order to change
				reorderable = !isSearch && !pickerMode,
			)
		}
	}

	/**
	 * Legacy `setContent` convenience for a container open: converts [bd]'s children
	 * (or the subjects at the root when [size] is 0) and loads them,
	 * reproducing the `size == 0` / container branches. Words and notes are
	 * leaves in the database model — they never take the list branch.
	 *
	 * @return [ContentKind.List] when children were loaded, [ContentKind.None] when
	 *   [bd] cannot hold any (the host skips its list work then).
	 */
	fun loadContent(bd: DbItem?, parent: DbItem?, size: Int): ContentKind {
		if (size == 0) {
			load(ItemRow.convert(ItemRepository.subjects(), ItemRepository.ROOT_ID))
			// the info strip reads the loaded list, so it must follow the load
			refreshInfo()
			return ContentKind.List
		}
		if (bd != null && bd.type == ItemKind.CHAPTER) {
			// a load replaces the models, so a previous list's selection is gone —
			// the legacy HierarchyAdapter ctor's deselect, via [load]
			load(ItemRow.convert(ItemRepository.children(bd.id), bd.id))
			refreshInfo()
			return ContentKind.List
		}
		return ContentKind.None
	}

	// ------------------------------------------------------------------ adapter parity

	/** Legacy `OpenListAdapter.addItem(item)` — appends and refreshes. */
	fun addItem(item: ItemRow) = insertItem(models.size, item)

	/** Legacy `OpenListAdapter.addItem(index, item)` — inserts and refreshes. */
	fun addItem(index: Int, item: ItemRow) = insertItem(index, item)

	/**
	 * Adds one row without remapping the list: the mapped UI list gains the one
	 * new entry, the position-column maximum grows by the row's position alone,
	 * and the keyed rows keep their identity — adding a word to a list of
	 * thousands costs one row, not a full rebuild. (The legacy adapter inserted
	 * one row and notified; this is its Compose equivalent.)
	 */
	private fun insertItem(index: Int, item: ItemRow) {
		models.add(index, item)
		_listState.update {
			it.copy(
				items = buildList {
					addAll(it.items)
					add(index, item.toUi())
				},
				maxPosition = maxOf(it.maxPosition, item.position),
			)
		}
	}

	/** Legacy `SearchAdapter.notifyDataSetChanged()` — re-reads the models. */
	fun notifyChanged() = remap()

	/** Refresh the info strip's element-count (legacy `es.setInfo` after list mutations). */
	fun refreshInfo() = setInfo(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2))

	/** The number of selected rows (the legacy `SearchAdapter.selected` counter). */
	fun selectionCount(): Int = models.count { it.selected }

	/** Legacy `ItemViewState.list.removeAll + notifyDataSetChanged` after delete/paste. */
	fun removeItems(items: Collection<ItemRow>) {
		models.removeAll(items)
		remap()
	}

	/** Read access for host flows that iterate the live models (legacy `adapter.list`). */
	fun items(): List<ItemRow> = models

	// ------------------------------------------------------------------ breadcrumbs

	/** Legacy `onChange(allChanged)` — rebuilds or extends the breadcrumb strip. */
	fun onChange(allChanged: Boolean) {
		_state.update {
			it.copy(
				breadcrumbs = backLog.path.map { it.name },
				insideContainer = backLog.path.isNotEmpty(),
			)
		}
		setInfo(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2))
	}

	/**
	 * Legacy `addPathButton`'s click listener, verbatim minus the adapter bookkeeping:
	 * jump to the breadcrumb at [index], popping [BackLog.onePath] same-path hops
	 * when they cover the distance, else pushing the truncated path as a new entry.
	 */
	fun breadcrumbClick(index: Int) {
		val bd = backLog.path.getOrNull(index) ?: return
		if (backLog.path.getOrNull(-1) === bd) return
		val path = EasyList<DbItem>()
		for (e in backLog.path) {
			path.add(e)
			if (e === bd) break
		}
		val diff = backLog.path.size - path.size
		val noChange: Boolean
		if (diff <= backLog.onePath.getOrNull(-1) ?: 0) {
			noChange = true
			host?.changedPath(diff)
		} else {
			noChange = false
			backLog.add(true, null, path)
		}
		host?.setContent(bd, backLog.path.getOrNull(-2), backLog.path.size)
		if (noChange) setInfo(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2)) else onChange(true)
	}

	// ------------------------------------------------------------------ info strip

	/**
	 * Legacy `setInfo(bd, parent)`: rebuilds the info strip text from the current
	 * list size + the container's success ratio/description. The ratio comes from
	 * [ItemRepository.recomputeSf] — the container's stored counters are refreshed
	 * against its words on every read (the legacy live object always knew them).
	 *
	 * The strip's collapsed/expanded presentation is no longer part of this state:
	 * the strip measures its own laid-out line count (a wrapped description has
	 * more lines than '\n' characters), so only the text travels through the state.
	 * Expansion is preserved across items — a non-expandable strip ignores the flag.
	 */
	fun setInfo(bd: DbItem?, parent: DbItem?) {
		val txt: String = if (backLog.path.isNotEmpty()) {
			val desc = bd!!.description
			val sf = ItemRepository.recomputeSf(bd.id)
			val ratio = if (sf.passed + sf.failed == 0) -1 else 100 * sf.passed / (sf.passed + sf.failed)
			GlobalDependencies.appContext.getString(
				R.string.data_children_sf, models.size, ratio,
				if (desc.isEmpty()) "%" else "%\n$desc",
			)
		} else GlobalDependencies.appContext.getString(R.string.data_child_count, models.size)
		_state.update { it.copy(infoText = txt) }
		// legacy `hsv.post { hsv.fullScroll(FOCUS_RIGHT) }` — scroll breadcrumbs to the end
		_state.update { it.copy(breadcrumbs = backLog.path.map { it.name }) }
	}

	/** Legacy info `OnClickListener` — toggles the expanded height (only when expandable). */
	fun toggleInfoExpanded() {
		_state.update { it.copy(isInfoExpanded = !it.isInfoExpanded) }
	}

	// ------------------------------------------------------------------ search input
	//
	// The whole input policy lives here: the shell field is a dumb view of
	// [query]. Any host (toolbar, picker) renders the shared field and forwards
	// raw text — threshold, debounce and clearing decisions stay in this file.

	/**
	 * Opens the field, keeping the last query for re-editing. A shown result
	 * list stays until typed input replaces it.
	 */
	fun openSearchField() {
		searchInputJob?.cancel()
		searchInputJob = null
		_searchFieldOpen.value = true
	}

	/** Closes the field. A shown result list stays; only the pending live run is dropped. */
	fun closeSearchField() {
		searchInputJob?.cancel()
		searchInputJob = null
		_searchFieldOpen.value = false
	}

	/**
	 * Back while the field is open: close it and end the run at once — the
	 * query is dropped, the run is aborted and the container's full list
	 * returns. A field opened before any run started (no input yet, or too
	 * short to run) only closes; the query text stays for the next open.
	 */
	fun cancelSearch() {
		closeSearchField()
		if (search) endSearch()
	}

	/**
	 * One keystroke batch: empty text ends the run at once, short text keeps
	 * the shown list, longer text runs once typing pauses ([SEARCH_DEBOUNCE_MS]).
	 */
	fun onQueryChange(text: String) {
		_query.value = text
		searchInputJob?.cancel()
		searchInputJob = null
		when (classifyLiveInput(text)) {
			LiveSearchAction.CLEAR -> { if (search) endSearch() }
			LiveSearchAction.IGNORE -> {}
			LiveSearchAction.RUN -> {
				val snapshot = text
				searchInputJob = viewModelScope.launch {
					delay(SEARCH_DEBOUNCE_MS)
					if (_query.value != snapshot) return@launch
					searchInputJob = null
					submitSearch(snapshot)
				}
			}
		}
	}

	/**
	 * Explicit submit (the keyboard search key): runs any non-empty text at
	 * once, bypassing the length gate. Empty text ends the run instead.
	 */
	fun onQuerySubmit(text: String) {
		searchInputJob?.cancel()
		searchInputJob = null
		_query.value = text
		if (text.isEmpty()) {
			if (search) endSearch()
			return
		}
		submitSearch(text)
	}

	// ------------------------------------------------------------------ search runs

	/**
	 * Legacy `SearchControl.onQueryTextSubmit` — searches the whole content of the
	 * currently opened container: the container's subgraph is loaded once
	 * ([ItemRepository.subgraph] + [ItemRepository.subgraphLinks] +
	 * [ItemRepository.subgraphChildLinks]) and a [SearchEngine] filters that
	 * preloaded [SearchIndex] in memory, with the unchanged matching grammar.
	 *
	 * The results stream: the walk (on `Dispatchers.IO`) converts each hit into
	 * a row from the preloaded index alone and feeds a channel; the main
	 * thread drains that channel in [SEARCH_BATCH] batches and appends each
	 * batch through [appendSearchRows], so rows appear as they are found, not
	 * in one block after the whole run. The per-batch append maps only the new
	 * rows and grows the position-column maximum by the last row's position.
	 *
	 * The run lives in [viewModelScope]: the preload, the filter loop and the
	 * row conversion run on `Dispatchers.IO`, the publishes fall through on the
	 * main thread, and a superseded run never publishes — [abortSearch] both
	 * clears the engine's [SearchEngine.active] (the blocking loop has no
	 * suspension point, so the job cancel alone cannot stop it) and cancels
	 * its [Job]. Clearing the view model cancels the live run with it.
	 *
	 * Navigation/abort semantics are preserved: the per-item loop checks
	 * [SearchEngine.active], which is cleared the moment a run is superseded.
	 *
	 * Live typing reuses this same entry: a run started while [search] is
	 * already `true` filters again without stacking another `BackLog` search
	 * hop, so keystrokes never fill the backlog. Only the first run of a
	 * search flow records the hop.
	 */
	fun submitSearch(q: String) {
		host?.onSearchStateChanged()
		val path = EasyList<DbItem>()
		path.addAll(backLog.path)
		val alreadySearching = search
		abortSearch()
		val eng = SearchEngine(q)
		engine = eng
		if (!eng.valid) return // bad regex: the engine ctor already showed the error and aborted
		// The search hop is a BackLog entry (legacy `backLog.add(false, null, null)`);
		// the currPath argument is unused by a same-path hop. A live re-run while
		// the result list is shown keeps the single stacked hop.
		if (!alreadySearching) backLog.add(false, null, backLog.path)
		search = true
		searchStart = System.currentTimeMillis()
		// start from an empty result list — the streamed batches fill it
		load(ArrayList(), isSearch = true)
		_state.update {
			it.copy(
				insideContainer = true, // the search hop kept the path non-empty
				infoText = GlobalDependencies.appContext.getString(R.string.data_child_count, 0),
				isInfoExpanded = false,
			)
		}
		searchJob = viewModelScope.launch {
			// the search bar is hidden at the subjects root, so a run always has one
			val root = path.getOrNull(-1) ?: return@launch
			val index = withContext(Dispatchers.IO) {
				SearchIndex(
					ItemRepository.subgraph(root.id),
					ItemRepository.subgraphLinks(root.id),
					ItemRepository.subgraphChildLinks(root.id),
				)
			}
			// the run may have been superseded while it preloaded
			if (!eng.active) return@launch
			// The producer walks and converts on IO (the conversion reads the
			// preloaded index only, no database); the consumer appends on the
			// main thread. Unlimited capacity: the walk is fast and the channel
			// keeps the batches flowing whatever pace the consumer drains at.
			val rows = Channel<ItemRow>(Channel.UNLIMITED)
			launch(Dispatchers.IO) {
				var position = 0
				eng.search(index, path) { bd, hitPath ->
					rows.trySend(ItemRow.of(bd, hitPath, ++position, index.linksOf(bd.id)))
				}
				rows.close()
			}
			var batch = ArrayList<ItemRow>(SEARCH_BATCH)
			for (row in rows) {
				batch.add(row)
				if (batch.size == SEARCH_BATCH) {
					appendSearchRows(batch)
					batch = ArrayList(SEARCH_BATCH)
				}
			}
			if (batch.isNotEmpty()) appendSearchRows(batch)
			// the run may have been superseded while the last batches drained
			if (!eng.active) return@launch
			ensureActive()
			val elapsed = System.currentTimeMillis() - searchStart
			val info = GlobalDependencies.appContext.getString(R.string.data_search_time, models.size, elapsed)
			lastSearchInfoText = info
			_state.update { it.copy(infoText = info) }
		}
	}

	/**
	 * Appends one streamed batch of search rows: `models` and the mapped UI
	 * list grow together (only the new rows are mapped — the existing UI items
	 * and their compose identities survive untouched), the position-column
	 * maximum follows the batch's last position, and the running result count
	 * plus elapsed time reach the info strip so the strip tracks the stream.
	 * [lastSearchRows] grows with each batch, so suspending the run mid-stream
	 * and coming back restores exactly what was shown.
	 */
	private fun appendSearchRows(batch: List<ItemRow>) {
		models.addAll(batch)
		val mapped = batch.map { it.toUi() }
		_listState.update {
			it.copy(
				items = it.items + mapped,
				maxPosition = maxOf(it.maxPosition, batch.last().position),
			)
		}
		lastSearchRows = ArrayList(models)
		val elapsed = System.currentTimeMillis() - searchStart
		lastSearchInfoText = GlobalDependencies.appContext
			.getString(R.string.data_search_time, models.size, elapsed)
		_state.update { it.copy(infoText = lastSearchInfoText) }
	}

	/**
	 * Ends the search: aborts the run, unwinds the search hops the run stacked
	 * (so a later back press leaves the container instead of silently consuming
	 * a hop), and restores the browse list of the current path through the host
	 * (menu/title work included) — search results replaced the live models, so
	 * leaving them in place would show hits under browse chrome.
	 */
	fun endSearch() {
		searchInputJob?.cancel()
		searchInputJob = null
		abortSearch()
		search = false
		lastSearchRows = emptyList()
		backLog.removeSearchHops()
		host?.setContent(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2), backLog.path.size)
		// the browse list is the live one again; remeasure the info strip against it
		refreshInfo()
	}

	/**
	 * Leaves the search-result screen for a container opened from a hit, but keeps
	 * the result rows cached and the search hop stacked: a later back press lands on
	 * that hop and [restoreSearch] brings the filtered list back. Only the run is
	 * aborted and the [search] flag dropped, so the host shows browse chrome for the
	 * opened container.
	 */
	fun suspendSearch() {
		searchInputJob?.cancel()
		searchInputJob = null
		abortSearch()
		search = false
	}

	/**
	 * Re-shows the last search result list when the current [BackLog] position is the
	 * stacked search run — i.e. a back press just returned to the search screen.
	 *
	 * @return `true` when the filtered list was restored; the host must then skip
	 *   loading the container's browse content and re-apply its search chrome.
	 */
	fun restoreSearch(): Boolean {
		if (search || lastSearchRows.isEmpty() || !backLog.onSearchHop()) return false
		load(lastSearchRows, isSearch = true)
		search = true
		_state.update {
			it.copy(
				infoText = lastSearchInfoText,
				isInfoExpanded = false,
				insideContainer = true,
			)
		}
		return true
	}

	/** Stops the live search (a new search / navigation away makes the old run inert). */
	private fun abortSearch() {
		engine?.active = false
		engine = null
		searchJob?.cancel()
		searchJob = null
	}

	// ------------------------------------------------------------------ navigation

	/**
	 * Legacy `ExplorerStuff.updateBackPath(skip)` — pops [skip] screens and restores
	 * the breadcrumbs/info. The native list has no adapter to restore, so the host
	 * follows with its own `setContent` reload (legacy did `VS.contentAdapter.update(rv)`).
	 *
	 * Note: this deliberately does NOT touch the search run. In the
	 * legacy code a back hop off the search results kept the underlying search run
	 * alive, so a live search must not be aborted on back navigation; a new
	 * [submitSearch] supersedes it via the adapter-type check ([SearchEngine.active]).
	 */
	fun updateBackPath(skip: Int) {
		var s = skip
		while (s > 0) {
			backLog.remove()
			s--
		}
		onChange(true)
	}

	// ------------------------------------------------------------------ row intents

	/**
	 * Legacy `MainFragment.onItemClick` / `SelectItemsFragment.onItemClick`, shared part:
	 * flip a word row. Honors the global `flipAllOnClick` setting: with it on,
	 * every visible word flips to the same side; otherwise only [item] flips.
	 */
	fun flipWord(item: ItemRow) {
		if (ItemRow.flipAllOnClick) {
			val flip = !item.flipped
			models.filter { it.item.type == ItemKind.WORD && it.flipped != flip }.forEach(ItemRow::flip)
		} else item.flip()
		remap()
	}

	/** Marks [item] selected/unselected and refreshes the derived counters. */
	fun setRowSelected(item: ItemRow, selected: Boolean) {
		if (item.selected == selected) return
		item.selected = selected
		remap()
	}

	/** Legacy `onItemLongClick`'s selection toggle (browse mode) for [item]. */
	fun toggleSelect(item: ItemRow) {
		setRowSelected(item, !item.selected)
		host?.onSelectionChanged()
	}

	/** Legacy check `ImageView` click — toggles selection without the long-press path. */
	fun setSelected(item: ItemRow, selected: Boolean) {
		setRowSelected(item, selected)
		host?.onSelectionChanged()
	}

	/**
	 * One drag-select step: the rows between the long-pressed anchor and the
	 * finger become selected; pulling back towards the anchor deselects the
	 * overshoot. [endRangeSelect] closes the gesture.
	 */
	fun selectRange(from: Int, to: Int) {
		setRangeSelected(from, to)
		host?.onSelectionChanged()
	}

	/** Ends a drag-select — its selection becomes the baseline of the next one. */
	fun endRangeSelect() {
		dragSelect = null
	}

	/** One live reorder step: move the row at [from] to [to] (not yet persisted). */
	fun moveRow(from: Int, to: Int) = move(from, to)

	/** Legacy `((CheckBox) select_all)` — mark every row selected/deselected. */
	fun selectAll(on: Boolean) {
		models.forEach { it.selected = on }
		remap()
		host?.onSelectionChanged()
	}

	/** Legacy `selected = -1` — drop selection entirely. */
	fun clearSelection() {
		models.forEach { it.selected = false }
		remap()
		host?.onSelectionChanged()
	}

	// ------------------------------------------------------------------ list internals

	/**
	 * One drag-select step. The dragged range is `[anchor]..[to]` (0-based, inclusive,
	 * the anchor being the long-pressed row the drag started from): the rows in it
	 * become selected, and a row the finger has pulled back past — back towards
	 * the anchor — returns to its state from before the drag, so an overshoot is
	 * undone instead of staying selected.
	 */
	fun setRangeSelected(anchor: Int, to: Int) {
		if (models.isEmpty()) return
		var snapshot = dragSelect
		if (snapshot == null || snapshot.first != anchor) {
			snapshot = anchor to BooleanArray(models.size) { models[it].selected }
			dragSelect = snapshot
		}
		val lo = anchor.coerceIn(0, models.size - 1)
		val hi = to.coerceIn(0, models.size - 1)
		val before = snapshot.second
		var changed = false
		for (i in models.indices) {
			// rows the drag never touched keep their pre-drag selection
			val want = i in minOf(lo, hi)..maxOf(lo, hi) || (i < before.size && before[i])
			if (models[i].selected != want) {
				models[i].selected = want
				changed = true
			}
		}
		if (changed) remap()
	}

	/** The running drag-select's anchor and the selection as it was before it began. */
	private var dragSelect: Pair<Int, BooleanArray>? = null

	/** Moves the row at [from] to [to] and renumbers the positions — one live reorder step. */
	private fun move(from: Int, to: Int) {
		if (from == to || from !in models.indices || to !in models.indices) return
		models.add(to, models.removeAt(from))
		models.forEachIndexed { i, row -> row.position = i + 1 }
		remap()
	}

	/** Recomputes the full UI item snapshot from the source models. */
	private fun remap() {
		val mapped = models.map { it.toUi() }
		_listState.update {
			it.copy(
				items = mapped,
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				showDesc = ItemRow.showDesc,
				selectionActive = selectionActive(it.pickerMode),
			)
		}
	}

	/**
	 * Whether rows must render their checkboxes: the host is a picker, or any
	 * row is selected. Computed on every publish so a long-press selection
	 * becomes visible immediately.
	 */
	private fun selectionActive(pickerMode: Boolean) =
		pickerMode || models.any { row -> row.selected }
}

/** What [ExplorerViewModel.loadContent] decided the list area shows. */
enum class ContentKind { List, None }
