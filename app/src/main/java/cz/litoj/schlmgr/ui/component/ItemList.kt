package cz.litoj.schlmgr.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.TranslationLine

/**
 * User-intent callbacks for the explorer list.
 * - defaults let previews and partial hosts provide only what they need.
 * - the row callbacks carry the affected [ItemUiModel].
 * - [ItemUiModel.payload] reaches the backing [ItemRow] when required.
 * - the drag and select callbacks carry indexes or nothing.
 */
class ItemListCallbacks(
	/** A tap on a row opens a container or flips a word, and the screen dispatches it. */
	val onItemClick: (ItemUiModel) -> Unit = {},
	/**
	 * A long-press on a row starts or toggles its selection.
	 * - the directory jump of a search hit lives in the more menu.
	 */
	val onItemLongClick: (ItemUiModel) -> Unit = {},
	/** Info icon tapped. Hosts traditionally open the text popup with the description. */
	val onInfo: (ItemUiModel) -> Unit = {},
	/** Reference icon tapped. The screen navigates to the source of the referenced row. */
	val onReference: (ItemUiModel) -> Unit = {},
	/** Info icon of an inner line of a flipped row, which opens its own description popup. */
	val onInnerInfo: (ItemUiModel, TranslationLine) -> Unit = { _, _ -> },
	/** A drag-select covered the rows from..to, 0-based and inclusive. */
	val onSelectRange: (Int, Int) -> Unit = { _, _ -> },
	/** A drag-select ended, so its selection becomes the new baseline. */
	val onSelectEnd: () -> Unit = {},
	/** A reorder drag moved the row at from onto to, live and not yet saved. */
	val onReorderMove: (Int, Int) -> Unit = { _, _ -> },
	/**
	 * A reorder drag moved the row and ended, so persist the current row order.
	 * - a round trip onto the same slot never fires it, and the long-press selection stays.
	 */
	val onReorderDrop: () -> Unit = {},
	/** A pull down at the very top of the list, where the host opens its search field. */
	val onTopPull: () -> Unit = {},
)

/**
 * The long-press-to-drag handoff, owned by [ItemList].
 * - the long-press arms a mode, see [arm].
 * - the drag claims it with [claim], and the gesture end clears it with [reset].
 * - a plain holder, not Compose state, so no step recomposes.
 */
internal class LongPressDragHandler {
	private var index: Int = -1
	private var mode: ListDragMode = ListDragMode.None

	val isArmed: Boolean get() = index >= 0

	fun arm(index: Int, mode: ListDragMode) {
		this.index = index
		this.mode = mode
	}

	/**
	 * Null when no press is waiting, otherwise the row and its snapshotted mode.
	 */
	fun claim(): Pair<Int, ListDragMode>? {
		if (index < 0) return null
		val claimed = index to mode
		index = -1
		return claimed
	}

	fun reset() {
		index = -1
		mode = ListDragMode.None
	}
}

/**
 * A selected list, the picker or a search-result list drags a selection.
 * - a result list has no container order to change.
 * - a plain reorderable container drags an order, and anything else ignores the drag.
 */
internal fun listDragMode(
	pickerMode: Boolean,
	selectionActive: Boolean,
	searchList: Boolean,
	reorderable: Boolean,
): ListDragMode = when {
	pickerMode || selectionActive || searchList -> ListDragMode.Select
	reorderable -> ListDragMode.Reorder
	else -> ListDragMode.None
}

/**
 * One keyed [Item] per row, where the selection is the background of the row.
 */
@Composable
fun ItemList(
	state: ItemListState,
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	/** The screen can persist a manual order, so a drag reorders instead of selecting. */
	reorderable: Boolean = false,
	/** The list state of the owning screen, kept so it can watch the scroll. */
	listState: LazyListState = rememberLazyListState(),
) {
	// maxPosition decides the one width every position cell takes, so all row numbers line up.
	// the view model maintains it per change, and it is measured once, not per row.
	val positionWidth = positionColumnWidth(
		state.maxPosition,
		state.variant,
	)
	val pending = remember { LongPressDragHandler() }
	// a structural change moves the rows, so a stale arm would act on the wrong ones.
	// the key is the content step, since a selection remap keeps the arm the press just set
	LaunchedEffect(state.contentStep) { pending.reset() }
	val dragMode = listDragMode(
		state.pickerMode,
		state.selectionActive,
		state.variant == ExplorerRowVariant.Search,
		reorderable,
	)
	val dragModifier = Modifier.rememberListDragModifier(
		listState = listState,
		contentStep = state.contentStep,
		pending = pending,
		onSelectRange = callbacks.onSelectRange,
		onSelectEnd = callbacks.onSelectEnd,
		onReorderMove = callbacks.onReorderMove,
		onReorderDrop = callbacks.onReorderDrop,
	)
	val pullModifier = Modifier.rememberTopPullModifier(listState, callbacks.onTopPull)
	LazyColumn(modifier.then(dragModifier).then(pullModifier), state = listState) {
		itemsIndexed(
			state.items,
			key = { _, item -> item.key },
			// recycling pools per row kind, so a reused composition keeps its type icon.
			// otherwise it would re-resolve the resource of another kind.
			contentType = { _, item -> item.kind },
		) { index, item ->
			Item(
				item = item,
				onClick = { callbacks.onItemClick(item) },
				// the press acts at once, because a selection starts immediately.
				// it snapshots the drag mode, which [rememberListDragModifier] then claims.
				onLongClick = {
					// the haptic rides on the long-press of combinedClickable
					pending.arm(index, dragMode)
					callbacks.onItemLongClick(item)
				},
				descVisible = !state.pickerMode,
				variant = state.variant,
				positionWidth = positionWidth,
				// every row keeps the placement spring, the dragged one included.
				// - a reorder moves rows between slots and the spring slides them there.
				// - so the dragged row is seen changing its place in the list.
				// - the size spring grows the box of a row whose height changed.
				// - the layout derives the offsets below it from that box.
				// - no placement animation can move a row entering at the edge.
				modifier = Modifier
					.animateItem(placementSpec = RowPlacementSpec)
					.animateContentSize(animationSpec = RowSizeSpec),
				infoAction = if (!state.pickerMode && item.description.isNotEmpty() && !ItemRow.showsDescription(item.description)) {
					{ InfoActionIcon { callbacks.onInfo(item) } }
				} else null,
				// references belong to the search view.
				// a browse row lives in its home, so there is nothing to jump to.
				referenceAction = if (!state.pickerMode && state.variant == ExplorerRowVariant.Search && item.hasSource) {
					{ ReferenceActionIcon { callbacks.onReference(item) } }
				} else null,
				onInnerInfo = if (!state.pickerMode) {
					{ line -> callbacks.onInnerInfo(item, line) }
				} else null,
			)
		}
	}
}

/**
 * The move springs of the rows, quick and without a bounce.
 * - they are not the stock StiffnessMediumLow glide that animateItem and animateContentSize use.
 */
private val RowPlacementSpec = spring<IntOffset>(
	stiffness = Spring.StiffnessMedium,
	dampingRatio = 1f,
)

private val RowSizeSpec = spring<IntSize>(
	stiffness = Spring.StiffnessMedium,
	dampingRatio = 1f,
)

@Composable
internal fun InfoActionIcon(onClick: () -> Unit) {
	ActionIcon(R.drawable.ic_desc_popup, onClick)
}

@Composable
internal fun ReferenceActionIcon(onClick: () -> Unit) {
	ActionIcon(R.drawable.ic_ref, onClick)
}

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

@Composable
private fun ItemListPreview() {
	// previews render [ItemUiModel] only and never reach the backing row,
	// so the payload stays at its default null
	val items = listOf(
		ItemUiModel("s1", 1, "Mathematics", "school subject", RowKind.SUBJECT,
			64, isReference = false, selected = false),
		ItemUiModel("s2", 2, "Imported subject", "", RowKind.SUBJECT,
			50, isReference = false, selected = false),
		ItemUiModel("c1", 3, "Integrals", "", RowKind.CHAPTER,
			-1, isReference = false, selected = false),
		ItemUiModel("w1", 4, "das Haus", "podstatné jméno", RowKind.WORD,
			100, isReference = false, selected = true),
		ItemUiModel("r1", 5, "see: Algebra", "", RowKind.WORD,
			-1, isReference = true, selected = false),
	)
	Column(Modifier.fillMaxSize()) {
		// descriptions stay on the default of the live setting.
		// a preview must not mutate process-global state that other previews read
		ItemList(
			ItemListState(items = items, maxPosition = items.size),
			ItemListCallbacks(),
			modifier = Modifier.weight(1f),
		)
		ItemList(
			ItemListState(items = items, maxPosition = items.size, pickerMode = true,
				variant = ExplorerRowVariant.Browse),
			ItemListCallbacks(),
			modifier = Modifier.weight(1f),
		)
	}
}
