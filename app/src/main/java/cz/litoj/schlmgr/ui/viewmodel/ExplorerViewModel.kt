package cz.litoj.schlmgr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.testing.TestEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The explorer state of one screen.
 * - actions arrive here and delegate the work to the injected data services.
 * - the chrome shows the published state.
 * - it owns what the top bar offers: the menu kind, the file doors and the search door.
 *
 * @param picker trims the rows to plain and keeps the select drag
 * @param io the blocking reads, injectable for tests
 */
abstract class ExplorerViewModel<R>(
	val history: NavigationHistory,
	private val picker: Boolean = false,
	protected val io: CoroutineDispatcher = Dispatchers.IO,
	settings: Settings? = null,
	protected val graphs: GraphQueries? = null,
) : ViewModel() {

	/** The content switch that every navigation funnels through. */
	protected open fun setContent(container: DbItem?, pulseItemId: Int? = null) {
		// any newer navigation supersedes a pending sibling read, even over the same row
		navigation.invalidate()
		loadContent(container, pulseItemId = pulseItemId)
	}

	/**
	 * Restores the screens after popping hops.
	 * - it re-shows a stacked search run, and otherwise reloads the browse list.
	 */
	fun changedPath(skip: Int) = navigation.changedPath(skip)

	fun openSibling(delta: Int) = navigation.openSibling(delta)

	/** Unlimited, so a request survives while the view is stopped. */
	private val _requests = Channel<R>(Channel.UNLIMITED)
	val requests = _requests.receiveAsFlow()

	protected fun emitRequest(request: R) {
		_requests.trySend(request)
	}

	// the seed mirrors the restored position, so the title stays correct before the first load.
	private val _state = MutableStateFlow(ExplorerScreenState(breadcrumbs = history.path.map { it.name }))
	val state: StateFlow<ExplorerScreenState> = _state.asStateFlow()

	protected fun updateState(transform: (ExplorerScreenState) -> ExplorerScreenState) = _state.update(transform)

	private val list = ExplorerList(viewModelScope, io, ListHost(), graphs)

	val listState: StateFlow<ItemListState> get() = list.listState

	protected val searching get() = list.searching

	/**
	 * The menu form of the open list, so no host picks a kind by hand.
	 * - both inputs ride the flows a screen collects, so its bar recomposes with them.
	 */
	val menuKind: ExplorerMenuKind get() = menuKindFor(searching, history.path.size)

	/**
	 * Why the search button cannot open, 0 when it can.
	 * - the root holds nothing to search, the rule [openSearchField] applies too.
	 * - a blocked search keeps its button, so the reason shows instead of the place vanishing.
	 */
	open val searchBlockedReason: Int
		get() = if (history.path.isEmpty()) R.string.unavailable_search_root else 0

	companion object {
		const val SEARCH_DEBOUNCE_MS = 300L
		const val EXIT_WINDOW_MS = 3000L
	}

	private val searchFlow = SearchFlow(viewModelScope, io, history, SearchFlowHost(), settings, graphs)

	private val navigation = ExplorerNavigation(viewModelScope, io, history, NavigationHost())

	private inner class NavigationHost : ExplorerNavigation.Host {
		override fun searching() = this@ExplorerViewModel.searching
		override fun clearSelection() = this@ExplorerViewModel.clearSelection()
		override suspend fun orderedChildren(parentId: Int) = list.sortedChildren(parentId)
		override fun setContent(container: DbItem?) = this@ExplorerViewModel.setContent(container)
		override fun leaveSearchFlow() {
			searchFlow.leaveSearchFlow(reload = false)
			setSearchUi(SearchUi.OFF)
		}
		override fun restoreSearch() = searchFlow.restoreSearch()
		override fun onChange() = this@ExplorerViewModel.onChange()
	}

	val query: StateFlow<String> get() = searchFlow.query

	internal val run get() = searchFlow.run

	private fun setSearchUi(mode: SearchUi) {
		_state.update { it.copy(searchUi = mode) }
	}

	private val selectionController = ExplorerSelectionController(SelectionControllerHost())

	/** An armed paste freezes reorder. */
	protected open fun pasteArmed() = false

	/**
	 * Cancels the host's armed move, true when one waited.
	 * - back runs it at the exit point, so the app never closes over it.
	 */
	protected open fun cancelArmedMove(): Boolean = false

	/** Whether the row gestures gather the batch across folders instead of selecting. */
	protected open fun gathering() = false

	/** The batch changed, an emptied one included. */
	protected open fun batchChanged() {}

	/** Whether a wholly held container stands for its rows, the picker of test sources. */
	protected open fun clumpsContainer() = false

	/** Whether the list shows [item], a browse load and a search batch alike. */
	protected open fun showsRow(item: DbItem) = true

	private inner class SelectionControllerHost : ExplorerSelectionController.Host {
		override val models get() = list.items()
		override fun openContainer() = list.containerRow
		override fun clumpsContainer() = this@ExplorerViewModel.clumpsContainer()
		override fun gathering() = this@ExplorerViewModel.gathering()
		override fun batchChanged() = this@ExplorerViewModel.batchChanged()
	}

	/** The gathered rows, whichever folders they came from. */
	fun batchRows(): List<ItemRow> = selectionController.rows

	/** The gather after a closing action leaves the current container view. */
	protected fun gatheredRowsForClose() = selectionController.gatheredRowsForClose()

	/** Whether a drag is adding its range right now. */
	protected fun batchDragging() = selectionController.dragging

	protected fun startBatch(rows: List<ItemRow>) = selectionController.startBatch(rows)

	protected fun clearBatch() = selectionController.clearBatch()

	fun refreshReorderable() = list.refreshReorderable()

	fun requestScrollToBottom() = list.requestScrollToBottom()

	/** The composable reports the live scroll, which the list keeps for history snapshots. */
	fun onScrollPosition(index: Int, offset: Int) = list.onScrollPosition(index, offset)

	fun load(
		content: List<ItemRow>,
		isSearch: Boolean = false,
		isPicker: Boolean = false,
		anchor: ScrollAnchor? = null,
	) = list.load(content, isSearch, isPicker, anchor)

	fun applyOrder(items: List<ItemRow>) = list.applyOrder(items)

	/** A pending navigation read refuses a reorder. */
	protected fun sort(comparator: Comparator<ItemRow>) = reorder(items().sortedWith(comparator))

	/** A random order of the shown rows, fresh on every pick. */
	protected fun random() = reorder(items().shuffled())

	private fun reorder(sorted: List<ItemRow>) {
		if (loadJob?.isActive == true) return
		applyOrder(sorted)
		refreshInfo()
	}

	/** The shared menu action comparator for one sort choice. */
	protected fun sortItem(itemId: Int): Boolean = when (ExplorerAction.of(itemId)) {
		ExplorerAction.SORT_ALPHA_AZ -> { sort(compareBy { it.toShow.lowercase() }); true }
		ExplorerAction.SORT_ALPHA_ZA -> { sort(compareBy<ItemRow> { it.toShow.lowercase() }.reversed()); true }
		ExplorerAction.SORT_SF_01 -> { sort(compareBy { it.item.ratio }); true }
		ExplorerAction.SORT_SF_10 -> { sort(compareBy<ItemRow> { it.item.ratio }.reversed()); true }
		ExplorerAction.SORT_PRIORITY -> { sort(compareByDescending { TestEngine.urgency(it.item) }); true }
		ExplorerAction.SORT_LENGTH_01 -> { sort(compareBy { it.toShow.length }); true }
		ExplorerAction.SORT_LENGTH_10 -> { sort(compareBy<ItemRow> { it.toShow.length }.reversed()); true }
		ExplorerAction.SORT_RANDOM -> { random(); true }
		ExplorerAction.SORT_DEFAULT -> { reloadDefaultOrder(); true }
		else -> false
	}

	/**
	 * The shared menu door: select all, the sort family and the clear filter.
	 * - a host answers its own actions first, then falls back here.
	 */
	open fun onMenuItemClick(itemId: Int): Boolean = when (ExplorerAction.of(itemId)) {
		ExplorerAction.CLEAR_FILTER -> { clearFilters(); true }
		ExplorerAction.SELECT_ALL -> { onSelectAllToggle(); true }
		else -> sortItem(itemId)
	}

	protected fun clearSortOrder() = list.clearSortOrder()

	/**
	 * The order a container had before this view sorted it.
	 * - the remembered sibling order drops, and the reload skips the sort of the view.
	 */
	protected fun reloadDefaultOrder() {
		val container = history.path.lastOrNull()
		clearSortOrder()
		loadContent(container, rememberSort = false)
	}

	/** The sibling order that the parent view displayed, rather than raw database order. */
	protected suspend fun orderedChildren(parentId: Int): List<DbItem> = list.sortedChildren(parentId)

	internal val loadJob get() = list.loadJob

	internal fun orderPersisted() = list.orderPersisted()

	fun loadContent(container: DbItem?, rememberSort: Boolean = true, pulseItemId: Int? = null) =
		list.loadContent(container, rememberSort, pulseItemId, history.path.map { it.id })

	fun addItem(item: ItemRow) = list.addItem(item)

	fun addItem(index: Int, item: ItemRow) = list.addItem(index, item)

	fun notifyChanged() = list.republish()

	fun selectionCount(): Int = list.selectionCount()

	fun removeItems(items: Collection<ItemRow>) = list.removeItems(items)

	fun items(): List<ItemRow> = list.items()

	/** The row of one shown slot, null when an index aged past the list. */
	fun rowAt(index: Int): ItemRow? = list.items().getOrNull(index)

	fun onChange() {
		_state.update {
			it.copy(
				breadcrumbs = history.path.map { it.name },
			)
		}
		setInfo(history.path.lastOrNull())
	}

	/**
	 * Jumps to a breadcrumb, whose path becomes the only ancestor chain of the view.
	 * - a jump from an unrelated branch drops that branch and the levels between them.
	 * - a stacked search hop consumes one pop, so drop it first.
	 */
	fun breadcrumbClick(index: Int) = navigation.breadcrumbClick(index)

	// the policy lives in [InfoStrip].

	private val infoStrip = InfoStrip(viewModelScope, io, InfoStripHost(), graphs)

	fun refreshInfo() = infoStrip.refreshInfo()

	fun setInfo(container: DbItem?) = infoStrip.setInfo(container)

	fun toggleInfoExpanded() = infoStrip.toggleExpanded()

	private inner class InfoStripHost : InfoStrip.Host {
		override val path get() = history.path
		override fun rows() = list.items()
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

	// the policy lives in [SearchFlow].

	/**
	 * Opens the search field, where [searchBlockedReason] keeps the door shut.
	 * @return true when the field opened now, so the pull reverse can hide it again
	 */
	fun openSearchField(): Boolean {
		if (searchBlockedReason != 0) return false
		// a field that already shows keeps its mode, so the pull reverse leaves it alone
		if (state.value.searchUi == SearchUi.TYPING) return false
		searchFlow.openField()
		return true
	}

	fun closeSearchField() = searchFlow.closeField()

	fun dismissSearch() = searchFlow.dismissField()

	/** The nav back out of a search: it ends and returns to the plain container view. */
	fun cancelSearch() = searchFlow.cancel()

	fun onQueryChange(text: String) = searchFlow.onTextChange(text)

	fun onQuerySubmit(text: String) = searchFlow.onSubmit(text)

	fun submitSearch(query: SearchQuery) = searchFlow.submitSearch(query)

	/** The query the filter editor reopens on. */
	fun searchQuery() = searchFlow.currentQuery()

	fun applyFilter(criterion: Criterion) = searchFlow.applyFilter(criterion)

	fun dropFilter(tag: SearchTag) = searchFlow.dropFilter(tag)

	fun clearFilters() = searchFlow.clearFilters()

	fun endSearch() = searchFlow.endSearch()

	fun restoreSearch(): Boolean = searchFlow.restoreSearch()

	fun refreshSearchRows() = searchFlow.refreshSearchRows()

	private inner class SearchFlowHost : SearchFlow.Host {
		override fun clearSelection() = this@ExplorerViewModel.clearSelection()

		// the picker marks are the sources it collects, so its search stays unlimited
		override fun markedRows(): Set<Int> =
			if (pickerMode()) emptySet()
			else list.items().filter { it.selected }.mapTo(LinkedHashSet()) { it.item.id }

		override val models get() = list.items()
		override fun appendRows(batch: List<ItemRow>) = list.append(batch.filter { showsRow(it.item) })
		override fun removeRows(items: Collection<ItemRow>) = list.removeItems(items)
		override fun republish() = list.republish()
		override fun discardSelection(rows: List<ItemRow>) = selectionController.discard(rows)
		override fun load(
			content: List<ItemRow>,
			isSearch: Boolean,
			isPicker: Boolean,
			anchor: ScrollAnchor?,
		) = this@ExplorerViewModel.load(content, isSearch, isPicker, anchor)

		override fun scrollAnchor(index: Int?, offset: Int?): ScrollAnchor = list.scrollAnchor(index, offset)

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

	/** A stacked search run stays stacked across the new container. */
	protected fun openChain(path: MutableList<DbItem>, pulseItemId: Int? = null) {
		history.push(path)
		if (searching) searchFlow.suspendSearch()
		setContent(path.lastOrNull(), pulseItemId)
	}

	/**
	 * The search rows survive, since they belong to the caller.
	 */
	fun updateBackPath(skip: Int, refreshStrip: Boolean = true) =
		navigation.updateBackPath(skip, refreshStrip)

	enum class BackOutcome {
		HANDLED,

		EXIT_HINT,

		EXIT,
	}

	/**
	 * Back, deepest layer out: selection, then search, then one level, then the armed move.
	 * - the root press goes through [registerExitPress].
	 */
	fun onBackRequested(): BackOutcome {
		if (selectionCount() > 0) {
			clearSelection()
			return BackOutcome.HANDLED
		}
		when (state.value.searchUi) {
			// the drop rule applies: a written query keeps the field with its results
			SearchUi.TYPING -> {
				dismissSearch()
				return BackOutcome.HANDLED
			}
			SearchUi.RESULTS -> {
				cancelSearch()
				return BackOutcome.HANDLED
			}
			SearchUi.OFF -> {}
		}
		// an armed move leaves before the exit press, so back never exits over it
		if (history.path.isNotEmpty()) {
			changedPath(1)
			return BackOutcome.HANDLED
		}
		if (cancelArmedMove()) return BackOutcome.HANDLED
		return if (registerExitPress()) BackOutcome.EXIT_HINT else BackOutcome.EXIT
	}

	internal var clock: () -> Long = System::currentTimeMillis
	private var backTime = 0L

	/**
	 * The double press means exiting the app.
	 *
	 * @return true on the first press, and false on the second inside the window
	 */
	protected open fun registerExitPress(): Boolean = if (clock() - backTime > EXIT_WINDOW_MS) {
		backTime = clock()
		true
	} else false

	/**
	 * A word tap flips the word.
	 * - the double of the explorer reads the row state at close time, no memory here.
	 */
	fun flipWord(item: ItemRow?) {
		val row = item ?: return
		list.flipWord(row)
	}

	/**
	 * The closing tap of a double, the explorer owns it.
	 * - every word and translation row flips to the side opposite the majority.
	 */
	fun onItemDoubleTap() {
		list.flipAllToMajority()
	}

	/**
	 * A row tap: a word flips and a chapter opens, while a host decides the rest.
	 * - a searched chapter opens through its own chain, so a deeper one keeps its path.
	 * - a non-word, non-chapter row reaches [onOtherRowClick].
	 */
	open fun onItemClick(item: ItemRow?) {
		val row = item ?: return
		// a marked row leaves its mark, since a gathered chapter carries all its content
		if (row.batched) {
			toggleSelect(row)
			return
		}
		when (row.item.type) {
			ItemKind.WORD -> flipWord(row)
			ItemKind.CHAPTER -> openRow(row)
			else -> onOtherRowClick(row, searchHit = row.path != null)
		}
	}

	private fun openRow(row: ItemRow) {
		val path = row.path
		if (path != null) {
			val chain = path.toMutableList()
			chain.add(row.item)
			openChain(chain)
			return
		}
		clearSelection()
		history.hop(row.item)
		setContent(row.item)
	}

	/** A row tap the base does not own, such as a note or an inert search hit. */
	protected open fun onOtherRowClick(row: ItemRow, searchHit: Boolean) {}

	/**
	 * The long-press starts a selection and keeps one, so the same press can drag it.
	 * - a gathering list toggles the row in its batch instead, and owns the gesture that follows.
	 */
	fun onItemLongClick(item: ItemRow?) = selectionController.onItemLongClick(item)

	fun toggleSelect(item: ItemRow?) = selectionController.toggleSelect(item, gathering())

	fun setSelected(item: ItemRow?, selected: Boolean) =
		selectionController.setSelected(item, selected, gathering())

	/** A drag that began on the batch keeps it, even when the drag emptied the paste. */
	fun selectRange(from: Int, to: Int) = selectionController.selectRange(from, to)

	/** A picker carries its own mark, while ordinary selection paints the pressed state. */
	fun selectRangeTo(from: Int, to: Int, selectedTo: Boolean) =
		if (gathering()) selectRange(from, to) else selectionController.setRangeSelected(from, to, selectedTo)

	fun endRangeSelect() = selectionController.endRangeSelect()

	fun setRangeSelected(anchor: Int, to: Int, selectedTo: Boolean? = null) =
		selectionController.setRangeSelected(anchor, to, selectedTo)

	fun moveRow(from: Int, to: Int) = list.move(from, to)

	fun selectAll(on: Boolean) = selectionController.selectAll(on)

	open fun onSelectAllToggle() = selectionController.onSelectAllToggle(gathering())

	fun clearSelection() = selectionController.clearSelection()

	private inner class ListHost : ExplorerList.Host {
		override val picker get() = this@ExplorerViewModel.picker
		override fun pasteArmed() = this@ExplorerViewModel.pasteArmed()

		override fun gathering() = this@ExplorerViewModel.gathering()

		override fun resetDragSelect() = selectionController.resetDragSelect()

		override fun rowsMarked(rows: List<ItemRow>) = selectionController.rowsMarked(rows)

		override fun viewShown(rows: List<ItemRow>) = selectionController.viewShown(rows)

		override fun viewLeft(rows: List<ItemRow>) = selectionController.viewLeft(rows)

		override fun showsRow(item: DbItem) = this@ExplorerViewModel.showsRow(item)

		override fun publishChrome(reorderable: Boolean) {
			_state.update {
				it.copy(
					// the crumbs follow each open and back.
					breadcrumbs = history.path.map { container -> container.name },
					reorderable = reorderable,
					contentStep = it.contentStep + 1,
				)
			}
		}

		override fun publishLoadedInfo(container: DbItem?, fresh: DbItem?) =
			infoStrip.publishLoadedInfo(container, fresh)

		override fun publishReorderable(reorderable: Boolean) =
			updateState { it.copy(reorderable = reorderable) }

		override fun refreshInfo() = this@ExplorerViewModel.refreshInfo()
	}
}
