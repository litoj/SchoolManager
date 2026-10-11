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
 * List store — live rows behind [ItemListState].
 * - host owns chrome — this class owns rows.
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

	fun orderPersisted() {
		listMutationGen++
	}

	/**
	 * Replaces items and publishes state.
	 * - search starts empty — batches stream via append.
	 * - drops drag-select — cancels the pending read.
	 */
	fun load(content: List<ItemRow>, isSearch: Boolean = false, isPicker: Boolean = false) {
		loadJob?.cancel()
		publish(content, isSearch, isPicker)
	}

	private fun publish(content: List<ItemRow>, isSearch: Boolean, isPicker: Boolean) {
		pickerMode = isPicker || host.picker
		host.resetDragSelect()
		models = content.toMutableList()
		_listState.update {
			it.copy(
				items = models.map { row -> row.toUi() },
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				variant = if (isSearch) ExplorerRowVariant.Search else ExplorerRowVariant.Browse,
				pickerMode = pickerMode,
				// fresh content carries no selection — the picker always selects
				selectionActive = selectionActive(pickerMode),
				contentStep = it.contentStep + 1,
			)
		}
		if (isSearch) host.onSearchRowsChanged()
		host.publishChrome(reorderableNow(isSearch))
	}

	/**
	 * Loads container open content from preloaded link set on [io].
	 * - mid-read mutation retries once.
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
			// the load landed — the null release lets the info refresh below publish
			loadJob = null
			// the info strip reads the loaded list, so it must follow the load
			host.refreshInfo()
		}
	}

	private fun readRows(container: DbItem?): List<ItemRow>? {
		if (container != null && container.type != ItemKind.CHAPTER) return null
		val parentId = container?.id ?: ROOT_ID
		val children = ItemRepository.children(parentId)
		// the default flip renders translations — one batch read for the
		// whole container, not one per word
		val translations =
			if (ItemRow.defFlip && children.any { it.type == ItemKind.WORD })
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
		// Index ages with popup — clamp to the live list.
		models.add(index.coerceIn(0, models.size), item)
		models.forEachIndexed { i, row -> row.position = i + 1 }
		_listState.update {
			it.copy(
				contentStep = it.contentStep + 1,
				items = List(models.size) { i ->
					val row = models[i]
					val old = it.items.getOrNull(i)
					// Row kept slot — reuse the mapped item.
					if (i < index && old?.payload === row && old.position == row.position &&
						old.selected == row.selected
					) {
						old
					} else row.toUi()
				},
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				selectionActive = selectionActive(it.pickerMode),
			)
		}
	}

	fun append(batch: List<ItemRow>) {
		batch.forEachIndexed { index, row -> row.position = models.size + 1 + index }
		models.addAll(batch)
		val mapped = batch.map { it.toUi() }
		_listState.update {
			it.copy(
				items = it.items + mapped,
				maxPosition = models.size,
				selectionActive = selectionActive(it.pickerMode),
			)
		}
	}

	fun selectionCount(): Int = models.count { it.selected }

	fun removeItems(items: Collection<ItemRow>) {
		listMutationGen++
		models.removeAll(items)
		// the removal leaves number gaps — renumber like a reorder step does
		models.forEachIndexed { i, row -> row.position = i + 1 }
		host.resetDragSelect()
		if (searching) host.onSearchRowsChanged()
		remap(structuralChange = true)
	}

	fun items(): List<ItemRow> = models

	fun refreshReorderable() {
		host.publishReorderable(reorderableNow(searching))
	}

	/**
	 * Shared tap rule — flips word row.
	 * - reads run on [io] — flips publish on main.
	 */
	fun flipWord(item: ItemRow?) {
		val row = item ?: return
		scope.launch {
			// Snapshot on main — the ids travel.
			val toFlip = if (ItemRow.flipAllOnClick) {
				val flip = !row.flipped
				models.filter { it.item.type == ItemKind.WORD && it.flipped != flip }
			} else listOf(row)
			val ids = toFlip.map { it.item.id }
			val translations = withContext(io) { ids.map { ItemRepository.translations(it) } }
			toFlip.forEachIndexed { i, target -> target.applyFlip(translations[i]) }
			remap()
		}
	}

	fun move(from: Int, to: Int) {
		if (from == to || from !in models.indices || to !in models.indices) return
		listMutationGen++
		// the baseline belongs to the pre-drag order.
		host.resetDragSelect()
		models.add(to, models.removeAt(from))
		models.forEachIndexed { i, row -> row.position = i + 1 }
		remap()
	}

	fun remap(structuralChange: Boolean = false) {
		val mapped = models.map { it.toUi() }
		_listState.update {
			it.copy(
				items = mapped,
				maxPosition = models.maxOfOrNull { it.position } ?: 1,
				selectionActive = selectionActive(it.pickerMode),
				contentStep = if (structuralChange) it.contentStep + 1 else it.contentStep,
			)
		}
	}

	private fun reorderableNow(isSearch: Boolean) = !isSearch && !pickerMode && !host.pasteArmed()

	private fun selectionActive(pickerMode: Boolean) =
		pickerMode || models.any { row -> row.selected }
}
