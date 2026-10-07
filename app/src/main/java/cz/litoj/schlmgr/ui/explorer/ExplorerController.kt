package cz.litoj.schlmgr.ui.explorer

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.ui.explorer.CurrentData.BackLog
import cz.litoj.schlmgr.ui.explorer.CurrentData.EasyList
import cz.litoj.schlmgr.ui.explorer.components.ItemListState
import cz.litoj.schlmgr.ui.explorer.search.SearchEngine
import cz.litoj.schlmgr.ui.explorer.search.SearchIndex
import cz.litoj.schlmgr.ui.explorer.viewmodel.ItemListViewModel
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import cz.litoj.schlmgr.ui.explorer.model.ExplorerRowVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The immutable screen state the Compose scaffold renders. Everything the legacy
 * `ExplorerStuff` read out of live Views (`path` buttons, the info strip's text and
 * collapsed/expanded height) or out of `VS` is captured here. The search field is
 * not part of it — it lives in the shell's app bar, shared by every explorer host —
 * and neither is the bottom strip: it is derived from the list view-model by the host.
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
 * The Compose-side counterpart of the deleted `ExplorerStuff` — it owns the breadcrumb
 * strip, the info strip and the wiring of the item list to [BackLog], exactly as the
 * legacy class did, but exposes a single immutable [ExplorerScreenState] flow instead
 * of mutating Views.
 *
 * The two browsing behaviours that the legacy code coupled to its hosts are injected:
 * - [host] performs the actual content switching for an open/close
 *   (legacy `Content.setContent`), menu/title work and selection-bar bookkeeping, and
 * - [onUiThread] posts view-facing work the legacy code sent through `rv.post`/`hsv.post`.
 *
 * The item list itself lives in [listVm]: browse lists and search results alike load
 * through it and the scaffold renders its state — the controller keeps no row
 * models of its own. [addItem]/[notifyChanged] mirror the old `OpenListAdapter`
 * calls the hosts still issue during edit/paste/import flows.
 */
class ExplorerController(
	val backLog: BackLog,
	private val host: Host,
	private val onUiThread: (Runnable) -> Unit,
	/** `true` for the SelectItemsFragment host: every row shows its checkbox. */
	private val picker: Boolean = false,
	private val listVm: ItemListViewModel = ItemListViewModel(),
) {

	/**
	 * The host-injected behaviours the legacy `ExplorerStuff` delegated to
	 * `MainFragment`/`SelectItemsFragment` through its `Content`/listener callbacks.
	 */
	interface Host {
		/**
		 * Legacy `Content.setContent` — the host loads [bd]'s children into the
		 * controller ([load] ) and does its container-specific menu/title/search work.
		 */
		fun setContent(bd: DbItem?, parent: DbItem?, pathLength: Int)

		/** The host's selection/paste strip state changed (legacy `setSelectOpts`/`setVisibleOpts`). */
		fun onSelectionChanged()

		/** A search was submitted / finished — the host clears its selection (legacy `onSearchSubmit`). */
		fun onSearchStateChanged()

		/** Legacy `BackUpdater.changedPath` — the host restores [skip] screens back. */
		fun changedPath(skip: Int)
	}

	private val _state = MutableStateFlow(ExplorerScreenState())
	val state: StateFlow<ExplorerScreenState> = _state.asStateFlow()

	/**
	 * The displayed item list — browse content and search results alike. Owned by
	 * the [ItemListViewModel]; the scaffold renders this directly, so search hits
	 * need no side channel.
	 */
	val listState: StateFlow<ItemListState> get() = listVm.state

	/** `true` while the current list is a search-result list (`SearchAdapter.search`). */
	var search = false
		private set

	/** Whether the host is the picker (checkboxes always on); set once by [load]. */
	private var pickerMode = false

	/** One run's dedup/visited sets and its abort flag. */
	private var engine: SearchEngine? = null
	/** Identity of the live search run; a superseded run's publish is dropped. */
	@Volatile
	private var searchToken: Any? = null
	@Volatile
	private var searchStart = 0L

	/**
	 * The last completed search's rows and info-strip text, kept so a back hop that
	 * lands on the stacked search run can restore the filtered list instead of the
	 * container's full contents (see [restoreSearch]).
	 */
	private var lastSearchRows: List<ItemRow> = emptyList()
	private var lastSearchInfoText = ""

	// ------------------------------------------------------------------ list loading

	/**
	 * Legacy `setContent`: replaces the displayed items. [content] is the converted
	 * [ItemRow] list (`ItemRow.convert(children, bd)`), [isSearch] marks a
	 * search-result list, [isPicker] fixes picker mode for
	 * the SelectItemsFragment host (see also the [picker] constructor flag).
	 *
	 * Every list — browse content and search results alike — loads through the
	 * ViewModel, which publishes the new state: unlike the legacy adapter swap
	 * there is no separate refresh call.
	 */
	fun load(content: List<ItemRow>, isSearch: Boolean = false, isPicker: Boolean = false) {
		search = isSearch
		pickerMode = isPicker || picker
		listVm.load(
			content,
			if (isSearch) ExplorerRowVariant.Search else ExplorerRowVariant.Browse,
			pickerMode,
		)
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
			// the legacy HierarchyAdapter ctor's deselect, via [ItemListViewModel.load]
			load(ItemRow.convert(ItemRepository.children(bd.id), bd.id))
			refreshInfo()
			return ContentKind.List
		}
		return ContentKind.None
	}

	// ------------------------------------------------------------------ adapter parity

	/** Legacy `OpenListAdapter.addItem(item)` — appends and refreshes. */
	fun addItem(item: ItemRow) = listVm.addItem(item)

	/** Legacy `OpenListAdapter.addItem(index, item)` — inserts and refreshes. */
	fun addItem(index: Int, item: ItemRow) = listVm.addItem(index, item)

	/** Legacy `SearchAdapter.notifyDataSetChanged()` — re-reads the models. */
	fun notifyChanged() = listVm.notifyChanged()

	/** Refresh the info strip's element-count (legacy `es.setInfo` after list mutations). */
	fun refreshInfo() = setInfo(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2))

	/** The number of selected rows (the legacy `SearchAdapter.selected` counter). */
	fun selectionCount(): Int = listVm.selectionCount()

	/** Legacy `ItemViewState.list.removeAll + notifyDataSetChanged` after delete/paste. */
	fun removeItems(items: Collection<ItemRow>) = listVm.removeItems(items)

	/** Read access for host flows that iterate the live models (legacy `adapter.list`). */
	fun items(): List<ItemRow> = listVm.items()

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
			host.changedPath(diff)
		} else {
			noChange = false
			backLog.add(true, null, path)
		}
		host.setContent(bd, backLog.path.getOrNull(-2), backLog.path.size)
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
				R.string.data_children_sf, listVm.items().size, ratio,
				if (desc.isEmpty()) "%" else "%\n$desc",
			)
		} else GlobalDependencies.appContext.getString(R.string.data_child_count, listVm.items().size)
		_state.update { it.copy(infoText = txt) }
		// legacy `hsv.post { hsv.fullScroll(FOCUS_RIGHT) }` — scroll breadcrumbs to the end
		_state.update { it.copy(breadcrumbs = backLog.path.map { it.name }) }
	}

	/** Legacy info `OnClickListener` — toggles the expanded height (only when expandable). */
	fun toggleInfoExpanded() {
		_state.update { it.copy(isInfoExpanded = !it.isInfoExpanded) }
	}

	// ------------------------------------------------------------------ search

	/**
	 * Legacy `SearchControl.onQueryTextSubmit` — searches the whole content of the
	 * currently opened container on a background thread: the container's subgraph
	 * is loaded once ([ItemRepository.subgraph] + [ItemRepository.subgraphLinks])
	 * and a [SearchEngine] filters that preloaded [SearchIndex] in memory, with the
	 * unchanged matching grammar. The hits then load through the ViewModel like
	 * any other list ([load] with `isSearch`), so the scaffold renders them from
	 * [listState] — no streaming side channel.
	 *
	 * Navigation/abort semantics are preserved: the per-item loop checks
	 * [SearchEngine.active], which this controller clears the moment a run is
	 * superseded, and the publish hop drops runs a newer search replaced.
	 */
	fun submitSearch(q: String) {
		host.onSearchStateChanged()
		val path = EasyList<DbItem>()
		path.addAll(backLog.path)
		abortSearch()
		val eng = SearchEngine(q)
		engine = eng
		if (!eng.valid) return // bad regex: the engine ctor already showed the error and aborted
		// The search hop is a BackLog entry (legacy `backLog.add(false, null, null)`);
		// the currPath argument is unused by a same-path hop.
		backLog.add(false, null, backLog.path)
		search = true
		// Fresh run token: a superseded run's publish hop can't clobber the live list.
		searchToken = Any()
		searchStart = System.currentTimeMillis()
		_state.update {
			it.copy(
				insideContainer = true, // the search hop kept the path non-empty
				infoText = GlobalDependencies.appContext.getString(R.string.data_child_count, 0),
				isInfoExpanded = false,
			)
		}
		val token = searchToken
		Thread({
			// the search bar is hidden at the subjects root, so a run always has one
			val root = path.getOrNull(-1) ?: return@Thread
			val index = SearchIndex(ItemRepository.subgraph(root.id), ItemRepository.subgraphLinks(root.id))
			val hits = ArrayList<Pair<DbItem, List<DbItem>>>()
			eng.search(index, path) { bd, hitPath -> hits.add(bd to hitPath) }
			val elapsed = System.currentTimeMillis() - searchStart
			onUiThread(Runnable {
				// the run may have been superseded while it filtered
				if (!eng.active || searchToken !== token) return@Runnable
				val rows = ArrayList<ItemRow>(hits.size)
				hits.forEachIndexed { i, (bd, hitPath) -> rows.add(ItemRow.of(bd, hitPath, i + 1)) }
				// keep the completed run for a possible back-restore
				lastSearchRows = rows
				load(rows, isSearch = true)
				val info = GlobalDependencies.appContext.getString(R.string.data_search_time, hits.size, elapsed)
				lastSearchInfoText = info
				_state.update { it.copy(infoText = info) }
			})
		}, "Search engine").start()
	}

	/**
	 * Ends the search: aborts the run, unwinds the search hops the run stacked
	 * (so a later back press leaves the container instead of silently consuming
	 * a hop), and restores the browse list of the current path through the host
	 * (menu/title work included) — search results replaced the live models, so
	 * leaving them in place would show hits under browse chrome.
	 */
	fun endSearch() {
		abortSearch()
		search = false
		lastSearchRows = emptyList()
		backLog.removeSearchHops()
		host.setContent(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2), backLog.path.size)
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
		searchToken = null
	}

	// ------------------------------------------------------------------ navigation

	/**
	 * Legacy `ExplorerStuff.updateBackPath(skip)` — pops [skip] screens and restores
	 * the breadcrumbs/info. The native list has no adapter to restore, so the host
	 * follows with its own `setContent` reload (legacy did `VS.contentAdapter.update(rv)`).
	 *
	 * Note: the controller deliberately does NOT touch the search run here. In the
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
	 * flip a word row. The hosts' open-a-container branch stays in the host (it differs
	 * between the two) and is driven through [openItem]/[ItemListCallbacks].
	 */
	fun flipWord(item: ItemRow) = listVm.flipWord(item)

	/** Legacy `onItemLongClick`'s selection toggle (browse mode) for [item]. */
	fun toggleSelect(item: ItemRow) {
		listVm.setRowSelected(item, !item.selected)
		host.onSelectionChanged()
	}

	/** Legacy check `ImageView` click — toggles selection without the long-press path. */
	fun setSelected(item: ItemRow, selected: Boolean) {
		listVm.setRowSelected(item, selected)
		host.onSelectionChanged()
	}

	/**
	 * One drag-select step: the rows between the long-pressed anchor and the
	 * finger become selected; pulling back towards the anchor deselects the
	 * overshoot. [endRangeSelect] closes the gesture.
	 */
	fun selectRange(from: Int, to: Int) {
		listVm.setRangeSelected(from, to)
		host.onSelectionChanged()
	}

	/** Ends a drag-select — its selection becomes the baseline of the next one. */
	fun endRangeSelect() {
		listVm.endRangeSelect()
	}

	/** One live reorder step: move the row at [from] to [to] (not yet persisted). */
	fun moveRow(from: Int, to: Int) = listVm.move(from, to)

	/** Legacy `((CheckBox) select_all)` — mark every row selected/deselected. */
	fun selectAll(on: Boolean) {
		listVm.selectAll(on)
		host.onSelectionChanged()
	}

	/** Legacy `selected = -1` — drop selection entirely. */
	fun clearSelection() {
		listVm.clearSelection()
		host.onSelectionChanged()
	}
}

/** What [ExplorerController.loadContent] decided the list area shows. */
enum class ContentKind { List, None }
