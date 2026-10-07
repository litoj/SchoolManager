package cz.litoj.schlmgr.ui.explorer.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.explorer.model.ExplorerRowVariant
import cz.litoj.schlmgr.ui.explorer.model.ItemUiModel
import cz.litoj.schlmgr.ui.explorer.model.ItemRow

/**
 * Display state of the explorer item list — replaces the visibility/ratio/selection
 * bookkeeping scattered across the legacy `SearchAdapter` fields.
 *
 * @property items rows to render, in order.
 * @property showDesc the global "show descriptions inline" setting
 *   (`ItemRow.showDesc`); when off, descriptions hide behind the info icon.
 * @property pickerMode the host is the item-picker activity
 *   (`SelectItemsFragment`): every row shows its checkbox, and descriptions/info
 *   icons stay hidden — exactly what the old adapter did when `selectActivity` was set.
 * @property selectionActive selection mode is on in a browsing host (the legacy
 *   `selected > -1`): checkboxes appear, descriptions and info keep their normal rules.
 * @property variant which legacy row layout the list replaces.
 */
data class ItemListState(
	val items: List<ItemUiModel> = emptyList(),
	val showDesc: Boolean = false,
	val pickerMode: Boolean = false,
	val selectionActive: Boolean = false,
	val variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
)

/**
 * User-intent callbacks for the explorer list. Defaults let previews and partial
 * hosts provide only what they need; each carries the affected row's [ItemUiModel],
 * whose [ItemUiModel.payload] reaches the backing [ItemRow] when required.
 */
class ItemListCallbacks(
	/** Tap on a row: open a container / flip a word (dispatch is the host's concern). */
	val onItemClick: (ItemUiModel) -> Unit = {},
	/** Long-press on a row: toggle selection / jump to the search hit's directory. */
	val onItemLongClick: (ItemUiModel) -> Unit = {},
	/** Selection checkbox toggled. */
	val onCheck: (ItemUiModel, Boolean) -> Unit = { _, _ -> },
	/** Info icon tapped — hosts traditionally open the text popup with the description. */
	val onInfo: (ItemUiModel) -> Unit = {},
	/** A drag-select covered the rows `[from]`..`[to]` (0-based, inclusive). */
	val onSelectRange: (Int, Int) -> Unit = { _, _ -> },
	/** A drag-select ended — its selection becomes the new baseline. */
	val onSelectEnd: () -> Unit = {},
	/** A reorder drag moved the row at `[from]` onto `[to]` (live, not yet saved). */
	val onReorderMove: (Int, Int) -> Unit = { _, _ -> },
	/** A reorder drag ended — persist the current row order. */
	val onReorderDrop: () -> Unit = {},
)

/**
 * The explorer item list — the Compose replacement of the explorer's `RecyclerView`
 * plus its `SearchAdapter`/`HierarchyAdapter`. Renders one keyed [Item] per entry of
 * [ItemListState.items]; trailing action visibility is derived from the state flags,
 * reproducing the legacy binding rules:
 *
 * - checkbox — [ItemListState.pickerMode] or [ItemListState.selectionActive],
 * - info icon — not [ItemListState.pickerMode], descriptions hidden
 *   (`!showDesc`), and the item actually has one.
 */
@Composable
fun ItemList(
	state: ItemListState,
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	listState: LazyListState = rememberLazyListState(),
	/** The host can persist a manual order: a long-press-drag then reorders instead of selecting. */
	reorderable: Boolean = false,
) {
	// All position cells reserve the width of the largest number, so the columns
	// line up however the count grows.
	val positionDigits = state.items.size.toString().length
	val haptics = LocalHapticFeedback.current
	val pending = remember { PendingLongPress() }
	// A selected list (or the picker) drags a selection; a plain container drags an order.
	val dragMode = when {
		state.pickerMode || state.selectionActive -> ListDragMode.Select
		reorderable -> ListDragMode.Reorder
		else -> ListDragMode.None
	}
	val dragModifier = rememberListDragModifier(
		listState = listState,
		pending = pending,
		onSelectRange = callbacks.onSelectRange,
		onSelectEnd = callbacks.onSelectEnd,
		onReorderMove = callbacks.onReorderMove,
		onReorderDrop = callbacks.onReorderDrop,
	)
	LazyColumn(modifier.then(dragModifier), state = listState) {
		itemsIndexed(
			state.items,
			key = { _, item -> item.key },
			contentType = { _, _ -> "explorer_item" },
		) { index, item ->
			Item(
				item = item,
				onClick = { callbacks.onItemClick(item) },
				// The press acts at once (a selection starts immediately) and snapshots the
				// drag mode; [rememberListDragModifier] only turns the same press into a drag.
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					pending.index = index
					pending.mode = dragMode
					callbacks.onItemLongClick(item)
				},
				descVisible = state.showDesc && !state.pickerMode,
				variant = state.variant,
				positionDigits = positionDigits,
				// The resize animates the row's height, so the rows shifted by it
				// follow frame by frame instead of snapping to the new offset —
				// animateItem alone covers only moves, not sibling-resize shifts.
				// The clip sits outside the animation: the content lays out at its
				// final size at once and Compose draws it beyond the node's bounds,
				// so without it the new lines appear instantly over the next row.
				modifier = Modifier.animateItem().clipToBounds().animateContentSize(),
				infoAction = if (!state.pickerMode && !state.showDesc && item.desc.isNotEmpty()) {
					{ InfoActionIcon { callbacks.onInfo(item) } }
				} else null,
				checkAction = if (state.pickerMode || state.selectionActive) {
					{ CheckActionIcon(item.selected) { callbacks.onCheck(item, !item.selected) } }
				} else null,
			)
		}
	}
}

/** `ic_desc_popup` action — opens the item's description (the `MessagePopup` trigger). */
@Composable
internal fun InfoActionIcon(onClick: () -> Unit) {
	ActionIcon(R.drawable.ic_desc_popup, onClick)
}

/** The simulated checkbox (drawable swap) used by selection mode. */
@Composable
internal fun CheckActionIcon(checked: Boolean, onClick: () -> Unit) {
	ActionIcon(if (checked) R.drawable.ic_check_box_filled else R.drawable.ic_check_box_empty, onClick)
}

/** A tappable 30dp trailing action icon — the legacy `ImageView` with its own click listener. */
@Composable
private fun ActionIcon(res: Int, onClick: () -> Unit) {
	Image(
		painterResource(res),
		contentDescription = null,
		modifier = Modifier
			.size(30.dp)
			.clickable(onClick = onClick),
	)
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF, name = "Mixed explorer list")
@Composable
private fun ItemListPreview() {
	// Renders read [ItemUiModel] only; the payload just has to exist. A word row
	// with flipping off is the one [ItemRow] that builds without the database.
	val payload = ItemRow(DbItem(name = "preview", description = "", type = ItemKind.WORD), null, 0, false)
	val items = listOf(
		ItemUiModel("s1", 1, "Mathematics", "school subject", R.drawable.ic_subject,
			64, isReference = false, isWord = false, flipped = false,
			selected = false, payload = payload),
		ItemUiModel("s2", 2, "Imported subject", "", R.drawable.ic_subject,
			50, isReference = false, isWord = false, flipped = false,
			selected = false, payload = payload),
		ItemUiModel("c1", 3, "Integrals", "", R.drawable.ic_chapter,
			-1, isReference = false, isWord = false, flipped = false,
			selected = false, payload = payload),
		ItemUiModel("w1", 4, "das Haus", "podstatné jméno", R.drawable.ic_word,
			100, isReference = false, isWord = true, flipped = false,
			selected = true, payload = payload),
		ItemUiModel("r1", 5, "see: Algebra", "", R.drawable.ic_ref,
			-1, isReference = true, isWord = false, flipped = false,
			selected = false, payload = payload),
	)
	Column(Modifier.fillMaxSize()) {
		ItemList(
			ItemListState(items = items, showDesc = true),
			ItemListCallbacks(),
			modifier = Modifier.weight(1f),
		)
		ItemList(
			ItemListState(items = items, pickerMode = true, variant = ExplorerRowVariant.Browse),
			ItemListCallbacks(),
			modifier = Modifier.weight(1f),
		)
	}
}
