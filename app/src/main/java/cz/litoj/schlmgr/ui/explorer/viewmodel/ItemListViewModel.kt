package cz.litoj.schlmgr.ui.explorer.viewmodel

import androidx.lifecycle.ViewModel
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import cz.litoj.schlmgr.ui.explorer.model.ExplorerRowVariant
import cz.litoj.schlmgr.ui.explorer.components.ItemListState
import cz.litoj.schlmgr.ui.explorer.model.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State holder for the [cz.litoj.schlmgr.ui.explorer.components.ItemList]
 * composable — the Compose replacement for the trio of mutable fields the legacy
 * adapters exposed (`SearchAdapter.selected`, `ItemRow.selected/flipped`,
 * `notifyDataSetChanged()` as the refresh mechanism).
 *
 * This ViewModel owns the row models and is the single source of the displayed
 * list: browse lists and search results alike load through [load] and every
 * mutation (flip, selection, add/remove) re-publishes a new immutable
 * [ItemListState]. Navigation/opening of containers and search-run semantics
 * stay with the hosts and their `BackLog`.
 */
class ItemListViewModel : ViewModel() {

	private val _state = MutableStateFlow(ItemListState())

	/** The list's display state; collected by Compose via `collectAsStateWithLifecycle()`. */
	val state: StateFlow<ItemListState> = _state.asStateFlow()

	/**
	 * Currently loaded row models, parallel to [ItemListState.items]
	 * ([load]'s mapping keeps the source model as the UI item's
	 * [cz.litoj.schlmgr.ui.explorer.model.ItemUiModel.key]).
	 */
	private var models = mutableListOf<ItemRow>()

	// ------------------------------------------------------------------ list contents

	/**
	 * Replaces the displayed content. [variant] picks the legacy row metrics
	 * (browse vs search results); search results keep their [ItemRow.path] so
	 * hosts can still jump to a hit's directory.
	 */
	fun load(
		content: List<ItemRow>,
		variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
		pickerMode: Boolean = false,
		showDesc: Boolean = ItemRow.showDesc,
	) {
		models = content.toMutableList()
		_state.update {
			it.copy(
				items = models.map { it.toUi() },
				variant = variant,
				pickerMode = pickerMode,
				showDesc = showDesc,
				// fresh content carries no selection; the picker always selects
				selectionActive = selectionActive(pickerMode),
			)
		}
	}

	/** Legacy `OpenListAdapter.addItem(item)` — appends and refreshes. */
	fun addItem(item: ItemRow) {
		models.add(item)
		remap()
	}

	/** Legacy `OpenListAdapter.addItem(index, item)` — inserts and refreshes. */
	fun addItem(index: Int, item: ItemRow) {
		models.add(index, item)
		remap()
	}

	/** Legacy `ItemViewState.list.removeAll + notifyDataSetChanged` after delete/paste. */
	fun removeItems(items: Collection<ItemRow>) {
		models.removeAll(items)
		remap()
	}

	/** Read access for host flows that iterate the live models (legacy `adapter.list`). */
	fun items(): List<ItemRow> = models

	/** Legacy `SearchAdapter.notifyDataSetChanged()` — re-reads the models. */
	fun notifyChanged() = remap()

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

	/**
	 * Ends the running drag-select: what it selected becomes the baseline, so the
	 * next drag starts from the current selection instead of a stale snapshot.
	 */
	fun endRangeSelect() {
		dragSelect = null
	}

	/** The running drag-select's anchor and the selection as it was before it began. */
	private var dragSelect: Pair<Int, BooleanArray>? = null

	/** Moves the row at [from] to [to] and renumbers the positions — one live reorder step. */
	fun move(from: Int, to: Int) {
		if (from == to || from !in models.indices || to !in models.indices) return
		models.add(to, models.removeAt(from))
		models.forEachIndexed { i, row -> row.position = i + 1 }
		remap()
	}

	/** Legacy `((CheckBox) select_all)` — mark every row selected/deselected. */
	fun selectAll(on: Boolean) {
		models.forEach { it.selected = on }
		remap()
	}

	/** Drops the selection flags of the current rows (a fresh load clears the mode itself). */
	fun clearSelection() {
		models.forEach { it.selected = false }
		remap()
	}

	/** The number of selected rows (the legacy `SearchAdapter.selected` counter). */
	fun selectionCount(): Int = models.count { it.selected }

	/** Recomputes the full UI item snapshot from the source models. */
	private fun remap() {
		val mapped = models.map { it.toUi() }
		_state.update {
			it.copy(
				items = mapped,
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
