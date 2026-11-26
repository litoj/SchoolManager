package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.db.ROOT_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PULSE_CLEAR_MS = 1_200L

/**
 * The list store, the live rows behind [ItemListState].
 * - the host owns the chrome, and this class owns the rows.
 */
internal class ExplorerList(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
	private val graphs: GraphQueries? = null,
) {

	interface Host {

		val picker: Boolean

		fun pasteArmed(): Boolean

		fun gathering(): Boolean

		fun resetDragSelect()

		/** Rows whose mark follows the gather, a new row or one search batch. */
		fun rowsMarked(rows: List<ItemRow>)

		/** The list shows a whole container view, a load or a re-sort. */
		fun viewShown(rows: List<ItemRow>)

		/** The list leaves the whole container view [rows] behind. */
		fun viewLeft(rows: List<ItemRow>)

		/** Whether a child of the container becomes a row at all. */
		fun showsRow(item: DbItem): Boolean

		fun publishChrome(reorderable: Boolean)

		fun publishLoadedInfo(container: DbItem?, fresh: DbItem?)

		fun publishReorderable(reorderable: Boolean)

		fun refreshInfo()
	}

	private val _listState = MutableStateFlow(ItemListState())

	val listState: StateFlow<ItemListState> = _listState.asStateFlow()

	val searching get() = _listState.value.variant == ExplorerRowVariant.Search

	private var pickerMode = false

	private var models = mutableListOf<ItemRow>()

	/** The container whose children the rows hold, the root id at the subjects screen. */
	private var currentContainerId = ROOT_ID

	/** The row of that container, so a wholly held view can collapse into it. */
	var containerRow: ItemRow? = null
		private set

	/** Tells the container loads apart, so each re-anchor request is a distinct one. */
	private var loadStep = 0

	var loadJob: Job? = null

	private var listMutationGen = 0

	private var scrollToBottomPending = false

	private val viewStates = ViewStateStore()

	private var pulseStep = 0

	private var pulseJob: Job? = null

	/** The live list scroll, shared by browse and search. */
	private var scrollIndex = 0

	private var scrollOffset = 0

	fun onScrollPosition(index: Int, offset: Int) {
		scrollIndex = index
		scrollOffset = offset
	}

	/** A new re-anchor request at the supplied position, or the live one by default. */
	fun scrollAnchor(index: Int? = null, offset: Int? = null) =
		ScrollAnchor(++loadStep, index ?: scrollIndex, offset ?: scrollOffset)

	/** The next publish moves the list to its last row. */
	fun requestScrollToBottom() {
		scrollToBottomPending = true
	}

	fun orderPersisted() {
		listMutationGen++
	}

	/** An explicit default sort drops only the remembered sibling order of this view. */
	fun clearSortOrder() = viewStates.clearOrder(currentContainerId)

	/** Sibling order of the current view, falling back to repository order on a first visit. */
	suspend fun sortedChildren(parentId: Int): List<DbItem> {
		val children = checkNotNull(graphs).children(parentId)
		val order = (if (parentId == ROOT_ID) viewStates.rootOrder else viewStates.orderOf(parentId)) ?: return children
		val position = HashMap<Int, Int>()
		for ((index, id) in order.withIndex()) position[id] = index
		return children.sortedBy { position[it.id] ?: Int.MAX_VALUE }
	}

	/**
	 * Replaces the items and publishes state.
	 * - search starts empty, since batches stream via append.
	 * - it drops the drag-select and cancels the pending read.
	 */
	fun load(
		content: List<ItemRow>,
		isSearch: Boolean = false,
		isPicker: Boolean = false,
		anchor: ScrollAnchor? = null,
	) {
		loadJob?.cancel()
		// a search shows rows of many containers, so no view there collapses into one
		if (isSearch) containerRow = null
		// leaving the browse view for a search keeps its state for the return
		if (isSearch && !searching) viewStates.capture(currentContainerId, scrollIndex, scrollOffset, models)
		publish(content, isSearch, isPicker, anchor = anchor)
	}

	/**
	 * Publishes the caller-sorted order of the current rows.
	 * - renumbering stays with the list, like its own inserts and moves.
	 */
	fun applyOrder(sorted: List<ItemRow>) {
		sorted.forEachIndexed { i, row -> row.renumber(i + 1) }
		load(sorted, isSearch = searching, isPicker = pickerMode)
	}

	private fun publish(
		content: List<ItemRow>,
		isSearch: Boolean,
		isPicker: Boolean,
		landedContainer: Int? = null,
		anchor: ScrollAnchor? = null,
		pulse: ItemPulse? = null,
	) {
		pickerMode = isPicker || host.picker
		host.resetDragSelect()
		models = content.toMutableList()
		host.viewShown(models)
		val bottomRequested = scrollToBottomPending
		val scroll = if (bottomRequested) {
			scrollToBottomPending = false
			_listState.value.scrollToBottom + 1
		} else _listState.value.scrollToBottom
		// a bottom request owns the position, so the re-anchor stands down
		val anchored = if (bottomRequested) null else anchor
		if (anchored != null) {
			scrollIndex = anchored.index
			scrollOffset = anchored.offset
		}
		_listState.update {
			it.copy(
				items = models.toList(),
				maxPosition = models.maxOfOrNull { row -> row.position } ?: 1,
				variant = if (isSearch) ExplorerRowVariant.Search else ExplorerRowVariant.Browse,
				pickerMode = pickerMode,
				contentStep = it.contentStep + 1,
				scrollToBottom = scroll,
				containerId = landedContainer ?: it.containerId,
				anchor = anchored ?: it.anchor,
				pulse = pulse,
				gathering = host.gathering(),
			)
		}
		host.publishChrome(reorderableNow(isSearch))
	}

	/**
	 * Loads the open content of a container from a preloaded link set on [io].
	 * - a mid-read mutation retries once.
	 * - the leaving view keeps its state for the way back.
	 */
	fun loadContent(
		container: DbItem?,
		rememberSort: Boolean = true,
		pulseItemId: Int? = null,
		pathIds: List<Int> = emptyList(),
	) {
		loadJob?.cancel()
		val containerId = container?.id ?: ROOT_ID
		// a reload stays in its view, while only navigation away may collapse the leaving picks
		if (containerId != currentContainerId &&
			containerRow?.item?.id != pathIds.getOrNull(pathIds.size - 2)) host.viewLeft(models)
		// search rows are not the view of the container, so they capture nothing
		if (!searching) viewStates.capture(currentContainerId, scrollIndex, scrollOffset, models)
		val saved = viewStates.moveTo(currentContainerId, containerId, pathIds)
		currentContainerId = containerId
		loadJob = scope.launch {
			var gen = listMutationGen
			while (true) {
				val content = withContext(io) {
					val rows = readRows(container) ?: return@withContext null
					val fresh = if (container == null) null else checkNotNull(graphs).get(container.id)
					Triple(rows, ownRow(container, pathIds), fresh)
				} ?: return@launch
				val (rows, own, fresh) = content
				if (gen == listMutationGen) {
					viewStates.applyFlips(containerId, rows)
					val ordered = if (rememberSort) viewStates.applyOrder(containerId, rows) else rows
					val target = pulseItemId?.let { id -> ordered.indexOfFirst { it.item.id == id } } ?: -1
					val pulse = if (target >= 0) ItemPulse(target, ++pulseStep) else null
					// the remembered place of the view, a first visit starts at the top
					val anchor = if (target >= 0) ScrollAnchor(++loadStep, target, 0)
					else ScrollAnchor(++loadStep, saved?.scrollIndex ?: 0, saved?.scrollOffset ?: 0)
					containerRow = own
					publish(ordered, isSearch = false, isPicker = false, landedContainer = containerId,
						anchor = anchor, pulse = pulse)
					host.publishLoadedInfo(container, fresh)
					clearPulseAfter(pulse)
					break
				}
				gen = listMutationGen
			}
			loadJob = null
		}
	}

	/** The pulse is one-shot, so a later scroll must not replay it. */
	private fun clearPulseAfter(pulse: ItemPulse?) {
		pulseJob?.cancel()
		if (pulse == null) return
		pulseJob = scope.launch {
			delay(PULSE_CLEAR_MS)
			_listState.update { if (it.pulse?.step == pulse.step) it.copy(pulse = null) else it }
		}
	}

	private suspend fun readRows(container: DbItem?): List<ItemRow>? {
		if (container != null && container.type != ItemKind.CHAPTER) return null
		val parentId = container?.id ?: ROOT_ID
		val graphs = checkNotNull(graphs)
		val children = graphs.children(parentId).filter(host::showsRow)
		// one batch read covers the container: the default render and every later flip.
		// a read per word would be too slow
		val translations =
			if (children.any { it.type == ItemKind.WORD }) graphs.childTranslations(parentId) else emptyMap()
		return ItemRow.convert(
			children, parentId, graphs.childLinksOf(parentId), translations,
		)
	}

	/**
	 * The row of [container] itself, so a wholly held view can collapse into it.
	 * - its parent reads from the path above it, and the read belongs on [io] with the rows.
	 */
	private suspend fun ownRow(container: DbItem?, pathIds: List<Int>): ItemRow? = container?.let {
		val parentId = pathIds.getOrNull(pathIds.size - 2) ?: ROOT_ID
		val links = checkNotNull(graphs).childLinksOf(parentId).filter { link -> link.childId == it.id }
		ItemRow.ofBrowse(it, parentId, 0, links, emptyList())
	}

	fun addItem(item: ItemRow) = insertItem(models.size, item)

	fun addItem(index: Int, item: ItemRow) = insertItem(index, item)

	private fun insertItem(index: Int, item: ItemRow) {
		listMutationGen++
		// the index ages with the popup, so clamp to the live list.
		val at = index.coerceIn(0, models.size)
		models.add(at, item)
		models.forEachIndexed { i, row -> row.renumber(i + 1) }
		host.resetDragSelect()
		host.rowsMarked(listOf(item))
		_listState.update {
			it.copy(
				contentStep = it.contentStep + 1,
				// a row appended at the end is new content, the list shows it
				scrollToBottom = if (at == models.lastIndex) it.scrollToBottom + 1 else it.scrollToBottom,
				items = models.toList(),
				maxPosition = models.maxOfOrNull { row -> row.position } ?: 1,
			)
		}
	}

	fun append(batch: List<ItemRow>) {
		batch.forEachIndexed { index, row -> row.renumber(models.size + 1 + index) }
		models.addAll(batch)
		host.rowsMarked(batch)
		_listState.update {
			it.copy(
				items = models.toList(),
				maxPosition = models.size,
			)
		}
	}

	fun selectionCount(): Int = models.count { it.selected }

	fun removeItems(items: Collection<ItemRow>) {
		listMutationGen++
		models.removeAll(items)
		// the removal leaves number gaps, so renumber like a reorder step does
		models.forEachIndexed { i, row -> row.renumber(i + 1) }
		host.resetDragSelect()
		republish(structuralChange = true)
	}

	fun items(): List<ItemRow> = models

	/** The armed paste freezes the reorder and gathers with the row icons. */
	fun refreshReorderable() {
		_listState.update { it.copy(gathering = host.gathering()) }
		host.publishReorderable(reorderableNow(searching))
	}

	/**
	 * The shared tap rule: a word flips to its translations, and a translation to its words.
	 * - the preloaded content flips at once, an unpreloaded row reads on io first.
	 */
	fun flipWord(item: ItemRow?) {
		val row = item ?: return
		val flippedTo = !row.flipped
		val generation = row.reserveFlip(flippedTo) ?: return
		scope.launch {
			val content = withContext(io) { ItemRow.readFlipContent(row.item, checkNotNull(graphs)) }
			row.completeFlip(generation, content, flippedTo)
		}
	}

	/**
	 * Flips every word and translation row to the side opposite the majority.
	 * - a double click drives it, no row anchors the flip.
	 * - the majority decides the side, a tie goes to the word side.
	 */
	fun flipAllToMajority() {
		val rows = models.filter {
			it.item.type == ItemKind.WORD || it.item.type == ItemKind.TRANSLATION
		}
		// the group flip lands on the minority side, a tie lands on the word side
		flipAllTo(rows.count { it.flipped } * 2 < rows.size)
	}

	/**
	 * Flips every word and translation row to [flipped].
	 * - the preloaded rows flip at once, an unpreloaded one reads on io.
	 */
	private fun flipAllTo(flipped: Boolean) {
		val pending = models.filter {
			(it.item.type == ItemKind.WORD || it.item.type == ItemKind.TRANSLATION) &&
				it.flipped != flipped
		}
		val unpreloaded = pending.mapNotNull { row ->
			row.reserveFlip(flipped)?.let { generation -> row to generation }
		}
		if (unpreloaded.isNotEmpty()) scope.launch {
			// one io hop reads every missing row, a hop per row repeats the dispatch
			val contents = withContext(io) {
				unpreloaded.map { (row, _) -> ItemRow.readFlipContent(row.item, checkNotNull(graphs)) }
			}
			unpreloaded.forEachIndexed { i, pendingFlip ->
				val (row, generation) = pendingFlip
				row.completeFlip(generation, contents[i], flipped)
			}
		}
	}

	/**
	 * Moves every selected row to the insert slot [to].
	 * - [to] counts the remaining rows, as the drag gesture reports it.
	 * - selected rows keep their shown order and land as one run.
	 * - false means the order stayed, so the drop persists nothing.
	 */
	fun move(from: Int, to: Int): Boolean {
		if (from !in models.indices) return false
		val movingRows = selectedRows(models.map { it.selected }, from).map { models[it] }
		val remainingRows = models - movingRows.toSet()
		if (to !in 0..remainingRows.size) return false
		val reordered = remainingRows.toMutableList().apply { addAll(to, movingRows) }
		if (reordered.indices.all { reordered[it] === models[it] }) return false
		listMutationGen++
		// the baseline belongs to the pre-drag order.
		host.resetDragSelect()
		models.clear()
		models.addAll(reordered)
		models.forEachIndexed { i, row -> row.renumber(i + 1) }
		republish()
		return true
	}

	/**
	 * Hands the list a fresh row list, for a change a row cannot publish itself.
	 * - a mark, a flip or an edit needs none, since the row owns its display state.
	 * - [structuralChange] also moves the rows, so a stale drag arm must not survive it.
	 */
	fun republish(structuralChange: Boolean = false) {
		_listState.update {
			it.copy(
				items = models.toList(),
				maxPosition = models.maxOfOrNull { row -> row.position } ?: 1,
				contentStep = if (structuralChange) it.contentStep + 1 else it.contentStep,
			)
		}
	}

	private fun reorderableNow(isSearch: Boolean) = !isSearch && !pickerMode && !host.pasteArmed()

}
