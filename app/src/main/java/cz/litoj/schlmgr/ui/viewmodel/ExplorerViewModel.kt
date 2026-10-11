package cz.litoj.schlmgr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.data.db.DbItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * One screen explorer state.
 * - actions go here and delegate the work to [cz.litoj.schlmgr.data.ItemRepository].
 * - chrome shows published state.
 *
 * @param history navigation history this screen reads and writes
 * @param picker selection mode stays on
 * @param io blocking reads, injectable for tests
 */
abstract class ExplorerViewModel<R>(
	val history: NavigationHistory,
	private val picker: Boolean = false,
	protected val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

	/**
	 * Content switch every navigation funnels through.
	 * - screens may override it.
	 */
	protected open fun setContent(container: DbItem?) = loadContent(container)

	/**
	 * Restores screens after popping hops.
	 * - re-shows stacked search run, else reloads browse list.
	 */
	fun changedPath(skip: Int) {
		// Strip refresh follows load.
		updateBackPath(skip, refreshStrip = false)
		// Back hop onto search run restores filtered list.
		if (restoreSearch()) return
		// Leaving search ends flow without reload.
		if (searching && history.onSearchHop()) {
			searchFlow.leaveSearchFlow(reload = false)
			setSearchUi(SearchUi.OFF)
		}
		setContent(history.path.lastOrNull())
		onChange()
	}

	/**
	 * Pending view-layer requests.
	 * - unlimited — it survives while the view is stopped.
	 */
	private val _requests = Channel<R>(Channel.UNLIMITED)
	val requests = _requests.receiveAsFlow()

	protected fun emitRequest(request: R) {
		_requests.trySend(request)
	}

	// Seed mirrors restored position, so the title stays correct before first load.
	private val _state = MutableStateFlow(ExplorerScreenState(breadcrumbs = history.path.map { it.name }))
	val state: StateFlow<ExplorerScreenState> = _state.asStateFlow()

	protected fun updateState(transform: (ExplorerScreenState) -> ExplorerScreenState) = _state.update(transform)

	private val list = ExplorerList(viewModelScope, io, ListHost())

	val listState: StateFlow<ItemListState> get() = list.listState

	protected val searching get() = list.searching

	companion object {
		const val SEARCH_MIN_CHARS = 2
		const val SEARCH_DEBOUNCE_MS = 300L
		const val EXIT_WINDOW_MS = 3000L
	}

	private val searchFlow = SearchFlow(viewModelScope, io, history, SearchFlowHost(), clock = { clock() })

	val query: StateFlow<String> get() = searchFlow.query

	internal val run get() = searchFlow.run

	private fun setSearchUi(mode: SearchUi) {
		_state.update { it.copy(searchUi = mode) }
	}

	/** The selection machine — see [Selection]. */
	private val selection = Selection(SelectionHost())

	// ------------------------------------------------------------------ list doors

	/**
	 * Whether paste is armed — armed paste freezes reorder, drag-select, navigation.
	 */
	protected open fun pasteArmed() = false

	fun refreshReorderable() = list.refreshReorderable()

	fun load(content: List<ItemRow>, isSearch: Boolean = false, isPicker: Boolean = false) =
		list.load(content, isSearch, isPicker)

	internal val loadJob get() = list.loadJob

	internal fun orderPersisted() = list.orderPersisted()

	fun loadContent(container: DbItem?) = list.loadContent(container)

	fun addItem(item: ItemRow) = list.addItem(item)

	fun addItem(index: Int, item: ItemRow) = list.addItem(index, item)

	fun notifyChanged() = list.remap()

	fun selectionCount(): Int = list.selectionCount()

	fun removeItems(items: Collection<ItemRow>) = list.removeItems(items)

	fun items(): List<ItemRow> = list.items()

	// ------------------------------------------------------------------ breadcrumbs

	fun onChange() {
		_state.update {
			it.copy(
				breadcrumbs = history.path.map { it.name },
			)
		}
		setInfo(history.path.lastOrNull())
	}

	/**
	 * Jumps to breadcrumb — pops [NavigationHistory.onePath] hops if they cover distance.
	 * - else pushes truncated path as new entry.
	 */
	fun breadcrumbClick(index: Int) {
		val container = history.path.getOrNull(index) ?: return
		if (history.path.lastOrNull() === container) return
		val path = mutableListOf<DbItem>()
		for (e in history.path) {
			path.add(e)
			if (e === container) break
		}
		// Stacked search hop consumes one pop — drop it first.
		if (history.onSearchHop()) {
			// Aborts run too — keeps the abort guard able to fire.
			searchFlow.leaveSearchFlow(reload = false)
			setSearchUi(SearchUi.OFF)
		}
		val diff = history.path.size - path.size
		// Hops cover distance, so only strip refresh is added.
		if (diff <= history.onePath.last()) {
			changedPath(diff)
			setInfo(history.path.lastOrNull())
		} else {
			history.push(path)
			setContent(container)
			onChange()
		}
	}

	// ------------------------------------------------------------------ info strip
	// Policy lives in [InfoStrip].

	private val infoStrip = InfoStrip(viewModelScope, io, InfoStripHost())

	fun refreshInfo() = infoStrip.refreshInfo()

	fun setInfo(container: DbItem?) = infoStrip.setInfo(container)

	fun toggleInfoExpanded() = infoStrip.toggleExpanded()

	private inner class InfoStripHost : InfoStrip.Host {
		override val path get() = history.path
		override fun listSize() = list.items().size
		override fun isSearching() = searching
		override fun loadPending() = list.loadJob?.isActive == true
		override var lastSearchInfo: InfoBar?
			get() = searchFlow.lastSearchInfo
			set(value) {
				searchFlow.lastSearchInfo = value
			}

		override fun publishInfo(info: InfoBar) {
			_state.update { it.copy(info = info) }
		}

		override fun publishInfoAndCrumbs(info: InfoBar) {
			_state.update { it.copy(info = info, breadcrumbs = history.path.map { row -> row.name }) }
		}

		override fun publishCrumbs() {
			_state.update { it.copy(breadcrumbs = history.path.map { row -> row.name }) }
		}

		override fun toggleExpanded() {
			_state.update { it.copy(isInfoExpanded = !it.isInfoExpanded) }
		}
	}

	// ------------------------------------------------------------------ search flow
	// Policy lives in [SearchFlow].

	fun openSearchField() = searchFlow.openField()

	fun closeSearchField() = searchFlow.closeField()

	fun onQueryChange(text: String) = searchFlow.onTextChange(text)

	fun onQuerySubmit(text: String) = searchFlow.onSubmit(text)

	fun submitSearch(q: String) = searchFlow.submitSearch(q)

	fun endSearch() = searchFlow.endSearch()

	fun restoreSearch(): Boolean = searchFlow.restoreSearch()

	fun refreshSearchRows() = searchFlow.refreshSearchRows()

	private inner class SearchFlowHost : SearchFlow.Host {
		override fun clearSelection() = this@ExplorerViewModel.clearSelection()
		override val models get() = list.items()
		override fun appendRows(batch: List<ItemRow>) = list.append(batch)
		override fun removeRows(items: Collection<ItemRow>) = list.removeItems(items)
		override fun remap() = list.remap()
		override fun load(content: List<ItemRow>, isSearch: Boolean, isPicker: Boolean) =
			this@ExplorerViewModel.load(content, isSearch, isPicker)

		override fun isSearching() = searching
		override fun pickerMode() = listState.value.pickerMode
		override fun searchUi() = state.value.searchUi
		override fun setSearchUi(mode: SearchUi) = this@ExplorerViewModel.setSearchUi(mode)
		override fun publishSearchInfo(info: InfoBar?) {
			_state.update { it.copy(info = info) }
		}

		override fun publishSearchInfoCollapsed(info: InfoBar?) {
			_state.update { it.copy(info = info, isInfoExpanded = false) }
		}

		override fun refreshInfo() = this@ExplorerViewModel.refreshInfo()
		override fun cancelPendingInfoRead() = infoStrip.cancelPendingRead()
		override fun setContent(container: DbItem?) = this@ExplorerViewModel.setContent(container)
	}

	// ------------------------------------------------------------------ navigation

	/**
	 * Replaces path and loads last container.
	 * - stacked search run stays stacked.
	 */
	protected fun openChain(path: MutableList<DbItem>) {
		history.push(path)
		if (searching) searchFlow.suspendSearch()
		setContent(path.lastOrNull())
	}

	/**
	 * Pops [skip] screens — restores crumbs and info only.
	 * - search rows belong to caller.
	 */
	fun updateBackPath(skip: Int, refreshStrip: Boolean = true) {
		var s = skip
		while (s > 0) {
			history.remove()
			s--
		}
		if (refreshStrip) onChange()
	}

	enum class BackOutcome {
		HANDLED,

		EXIT_HINT,

		EXIT,
	}

	/**
	 * Back, deepest layer out — selection, then search, then one level.
	 * - root double press exits.
	 */
	fun onBackRequested(): BackOutcome {
		if (selectionCount() > 0) {
			clearSelection()
			return BackOutcome.HANDLED
		}
		when (state.value.searchUi) {
			SearchUi.TYPING -> {
				closeSearchField()
				return BackOutcome.HANDLED
			}
			SearchUi.RESULTS -> {
				endSearch()
				return BackOutcome.HANDLED
			}
			SearchUi.OFF -> {}
		}
		if (history.path.isNotEmpty()) {
			changedPath(1)
			return BackOutcome.HANDLED
		}
		return if (registerExitPress()) BackOutcome.EXIT_HINT else BackOutcome.EXIT
	}

	internal var clock: () -> Long = System::currentTimeMillis
	private var backTime = 0L

	/**
	 * Records root back press in exit window.
	 * @return true on first press, false on second inside window.
	 */
	protected fun registerExitPress(): Boolean = if (clock() - backTime > EXIT_WINDOW_MS) {
		backTime = clock()
		true
	} else false

	// ------------------------------------------------------------------ row intents

	fun flipWord(item: ItemRow?) = list.flipWord(item)

	private inner class SelectionHost : Selection.Host {
		override val models get() = list.items()
		override fun remap() = list.remap()
		override fun pasteArmed() = this@ExplorerViewModel.pasteArmed()
	}

	fun toggleSelect(item: ItemRow?) = selection.toggleSelect(item)

	fun setSelected(item: ItemRow?, selected: Boolean) = selection.setSelected(item, selected)

	fun selectRange(from: Int, to: Int) = selection.selectRange(from, to)

	fun endRangeSelect() = selection.endRangeSelect()

	fun setRangeSelected(anchor: Int, to: Int) = selection.setRangeSelected(anchor, to)

	fun moveRow(from: Int, to: Int) = list.move(from, to)

	fun selectAll(on: Boolean) = selection.selectAll(on)

	fun onSelectAllToggle() = selection.onSelectAllToggle()

	fun clearSelection() = selection.clearSelection()

	// ------------------------------------------------------------------ list host

	private inner class ListHost : ExplorerList.Host {
		override val picker get() = this@ExplorerViewModel.picker
		override fun pasteArmed() = this@ExplorerViewModel.pasteArmed()

		override fun resetDragSelect() = selection.reset()

		override fun onSearchRowsChanged() = searchFlow.snapshotRestoreRows()

		override fun publishChrome(reorderable: Boolean) {
			_state.update {
				it.copy(
					// Crumbs follow each open and back.
					breadcrumbs = history.path.map { container -> container.name },
					reorderable = reorderable,
					contentStep = it.contentStep + 1,
				)
			}
		}

		override fun publishReorderable(reorderable: Boolean) =
			updateState { it.copy(reorderable = reorderable) }

		override fun refreshInfo() = this@ExplorerViewModel.refreshInfo()
	}
}
