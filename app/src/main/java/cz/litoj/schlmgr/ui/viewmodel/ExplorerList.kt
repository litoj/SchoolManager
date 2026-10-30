package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ROOT_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The list store, the live rows behind [ItemListState].
 * - the host owns the chrome, and this class owns the rows.
 */
internal class ExplorerList(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {

		val picker: Boolean

		fun pasteArmed(): Boolean

		fun resetDragSelect()

		fun onSearchRowsChanged()

		fun publishChrome(reorderable: Boolean)

		fun publishReorderable(reorderable: Boolean)

		fun refreshInfo()
	}

	private val _listState = MutableStateFlow(ItemListState())

	val listState: StateFlow<ItemListState> = _listState.asStateFlow()

	val searching get() = _listState.value.variant == ExplorerRowVariant.Search

	private var pickerMode = false

	private var models = mutableListOf<ItemRow>()

	var loadJob: Job? = null

	private var listMutationGen = 0

	private var scrollToBottomPending = false

	/** The next publish moves the list to its last row. */
	fun requestScrollToBottom() {
		scrollToBottomPending = true
	}

	fun orderPersisted() {
		listMutationGen++
	}

	/**
	 * Replaces the items and publishes state.
	 * - search starts empty, since batches stream via append.
	 * - it drops the drag-select and cancels the pending read.
	 */
	fun load(content: List<ItemRow>, isSearch: Boolean = false, isPicker: Boolean = false) {
		loadJob?.cancel()
		publish(content, isSearch, isPicker)
	}

	/**
	 * Publishes the caller-sorted order of the current rows.
	 * - renumbering stays with the list, like its own inserts and moves.
	 */
	fun applyOrder(sorted: List<ItemRow>) {
		sorted.forEachIndexed { i, row -> row.renumber(i + 1) }
		load(sorted, isSearch = searching, isPicker = pickerMode)
	}

	private fun publish(content: List<ItemRow>, isSearch: Boolean, isPicker: Boolean) {
		pickerMode = isPicker || host.picker
		host.resetDragSelect()
		models = content.toMutableList()
		val scroll = if (scrollToBottomPending) {
			scrollToBottomPending = false
			_listState.value.scrollToBottom + 1
		} else _listState.value.scrollToBottom
		_listState.update {
			it.copy(
				items = models.map { row -> row.toUi() },
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				variant = if (isSearch) ExplorerRowVariant.Search else ExplorerRowVariant.Browse,
				pickerMode = pickerMode,
				// fresh content carries no selection
				selectionActive = selectionActive(),
				contentStep = it.contentStep + 1,
				scrollToBottom = scroll,
			)
		}
		if (isSearch) host.onSearchRowsChanged()
		host.publishChrome(reorderableNow(isSearch))
	}

	/**
	 * Loads the open content of a container from a preloaded link set on [io].
	 * - a mid-read mutation retries once.
	 */
	fun loadContent(container: DbItem?) {
		loadJob?.cancel()
		loadJob = scope.launch {
			var gen = listMutationGen
			while (true) {
				val rows = withContext(io) { readRows(container) } ?: return@launch
				if (gen == listMutationGen) {
					publish(rows, isSearch = false, isPicker = false)
					break
				}
				gen = listMutationGen
			}
			// the load landed, so the null release lets the info refresh below publish
			loadJob = null
			// the info strip reads the loaded list, so it must follow the load
			host.refreshInfo()
		}
	}

	private fun readRows(container: DbItem?): List<ItemRow>? {
		if (container != null && container.type != ItemKind.CHAPTER) return null
		val parentId = container?.id ?: ROOT_ID
		val children = ItemRepository.children(parentId)
		// one batch read covers the container: the default render and every later flip.
		// a read per word would be too slow
		val translations =
			if (children.any { it.type == ItemKind.WORD })
				ItemRepository.childTranslations(parentId) else emptyMap()
		return ItemRow.convert(
			children, parentId,
			ItemRepository.childLinksOf(parentId), translations,
		)
	}

	fun addItem(item: ItemRow) = insertItem(models.size, item)

	fun addItem(index: Int, item: ItemRow) = insertItem(index, item)

	private fun insertItem(index: Int, item: ItemRow) {
		listMutationGen++
		// the index ages with the popup, so clamp to the live list.
		val at = index.coerceIn(0, models.size)
		models.add(at, item)
		models.forEachIndexed { i, row -> row.renumber(i + 1) }
		_listState.update {
			it.copy(
				contentStep = it.contentStep + 1,
				// a row appended at the end is new content, the list shows it
				scrollToBottom = if (at == models.lastIndex) it.scrollToBottom + 1 else it.scrollToBottom,
				items = List(models.size) { i ->
					val row = models[i]
					val old = it.items.getOrNull(i)
					// the row kept its slot, so reuse the mapped item.
					if (i < at && old?.payload === row && old.position == row.position &&
						old.selected == row.selected
					) {
						old
					} else row.toUi()
				},
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				selectionActive = selectionActive(),
			)
		}
	}

	fun append(batch: List<ItemRow>) {
		batch.forEachIndexed { index, row -> row.renumber(models.size + 1 + index) }
		models.addAll(batch)
		val mapped = batch.map { it.toUi() }
		_listState.update {
			it.copy(
				items = it.items + mapped,
				maxPosition = models.size,
				selectionActive = selectionActive(),
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
		if (searching) host.onSearchRowsChanged()
		remap(structuralChange = true)
	}

	fun items(): List<ItemRow> = models

	fun refreshReorderable() {
		host.publishReorderable(reorderableNow(searching))
	}

	/**
	 * The shared tap rule: a word flips to its translations, and a translation to its words.
	 * - the preloaded content flips at once, an unpreloaded row reads on io first.
	 */
	fun flipWord(item: ItemRow?) {
		val row = item ?: return
		if (row.flipOnto(!row.flipped)) {
			remap()
			return
		}
		scope.launch {
			val content = withContext(io) { ItemRow.flipContentOf(row.item) }
			row.preloadFlip(content)
			row.applyFlip(content)
			remap()
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
		val unpreloaded = pending.filterNot { it.flipOnto(flipped) }
		if (unpreloaded.isNotEmpty()) scope.launch {
			// one io hop reads every missing row, a hop per row repeats the dispatch
			val contents = withContext(io) { unpreloaded.map { ItemRow.flipContentOf(it.item) } }
			unpreloaded.forEachIndexed { i, row ->
				row.preloadFlip(contents[i])
				row.flipOnto(flipped)
			}
			remap()
		}
		remap()
	}

	fun move(from: Int, to: Int) {
		if (from == to || from !in models.indices || to !in models.indices) return
		listMutationGen++
		// the baseline belongs to the pre-drag order.
		host.resetDragSelect()
		models.add(to, models.removeAt(from))
		models.forEachIndexed { i, row -> row.renumber(i + 1) }
		remap()
	}

	fun remap(structuralChange: Boolean = false) {
		val mapped = models.map { it.toUi() }
		_listState.update {
			it.copy(
				items = mapped,
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				selectionActive = selectionActive(),
				contentStep = if (structuralChange) it.contentStep + 1 else it.contentStep,
			)
		}
	}

	private fun reorderableNow(isSearch: Boolean) = !isSearch && !pickerMode && !host.pasteArmed()

	private fun selectionActive() = models.any { row -> row.selected }
}
