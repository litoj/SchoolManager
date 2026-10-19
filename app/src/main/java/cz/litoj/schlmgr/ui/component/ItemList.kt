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
	 * - the row callbacks carry the affected [ItemUiModel], whose [ItemUiModel.payload] reaches the backing [ItemRow] when required.
 * - the drag/select ones carry indexes or nothing.
 */
class ItemListCallbacks(
	/** A tap on a row opens a container / flips a word (dispatch is the screen's concern). */
	val onItemClick: (ItemUiModel) -> Unit = {},
	/**
	 * A long-press on a row starts (or toggles) its selection — the search
	 * hit's directory jump lives in the more menu.
	 */
	val onItemLongClick: (ItemUiModel) -> Unit = {},
	/** Info icon tapped — hosts traditionally open the text popup with the description. */
	val onInfo: (ItemUiModel) -> Unit = {},
	/** Reference icon tapped — the screen navigates to the referenced row's source. */
	val onReference: (ItemUiModel) -> Unit = {},
	/** Info icon of a flipped row's inner line — its own description's popup. */
	val onInnerInfo: (ItemUiModel, TranslationLine) -> Unit = { _, _ -> },
	/** A drag-select covered the rows `from..to` (0-based, inclusive). */
	val onSelectRange: (Int, Int) -> Unit = { _, _ -> },
	/** A drag-select ended — its selection becomes the new baseline. */
	val onSelectEnd: () -> Unit = {},
	/** A reorder drag moved the row at `from` onto `to` (live, not yet saved). */
	val onReorderMove: (Int, Int) -> Unit = { _, _ -> },
	/** A reorder drag moved the row and ended — persist the current row order. A round trip back onto the row's own slot never fires it — the long-press's selection stays. */
	val onReorderDrop: () -> Unit = {},
	/** A pull down at the list's very top — the host opens its search field. */
	val onTopPull: () -> Unit = {},
)

/**
 * The long-press-to-drag handoff, owned by [ItemList]. The long-press
 * arms a mode ([arm]), the drag claims it ([claim]), the gesture end
 * clears it ([reset]).
 * - a plain holder, not Compose state — no step recomposes.
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
	 * Takes the waiting arm for a drag (clears the pending index, returns the row
	 * and its snapshotted mode), or `null` when no press is waiting.
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
 * The drag a long-press starts on this list.
 * - a selected list, the picker or a search-result list drags a selection
 *   (a result list has no container order to change).
 * - a plain reorderable container drags an order. Anything else drags nothing.
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
 * One keyed [Item] per row. The selection is the row's own background.
 * - info icon — not picker, descriptions hidden, item has one.
 * - reference icon — not picker, row lives elsewhere too.
 */
@Composable
fun ItemList(
	state: ItemListState,
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	/** The screen can persist a manual order — a long-press-drag then reorders instead of selecting. */
	reorderable: Boolean = false,
	/** The owning screen's list state — kept so it can watch the scroll. */
	listState: LazyListState = rememberLazyListState(),
) {
	// The state's maxPosition decides the one width every position cell takes, so
	// the numbers of all rows line up. The view model maintains it per change
	// (never by rescanning the items here), and it is measured once, not per row.
	val positionWidth = positionColumnWidth(
		state.maxPosition,
		state.variant,
	)
	val pending = remember { LongPressDragHandler() }
	// a structural change moves the rows — an arm from the stale layout would
	// act on the wrong ones. Keyed on the content step — the press's own
	// selection remap keeps the arm it just set
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
			// recycling pools per row kind — a reused composition keeps its
			// type icon instead of re-resolving another kind's resource
			contentType = { _, item -> item.kind },
		) { index, item ->
			Item(
				item = item,
				onClick = { callbacks.onItemClick(item) },
				// The press acts at once (a selection starts immediately) and snapshots the
				// drag mode. [rememberListDragModifier] turns the same press into a drag.
				onLongClick = {
					// the haptic rides on combinedClickable's own long-press
					pending.arm(index, dragMode)
					callbacks.onItemLongClick(item)
				},
				descVisible = !state.pickerMode,
				variant = state.variant,
				positionWidth = positionWidth,
				// Every row, the dragged one included, keeps the placement spring — a reorder
				// moves rows between slots and the spring slides them there, so the dragged
				// row is seen changing its place in the list. The size spring grows the box
				// of a row whose height changed, and the layout derives the offsets below it
				// from the box — down to a row entering at the edge, which no placement
				// animation can move.
				modifier = Modifier
					.animateItem(placementSpec = RowPlacementSpec)
					.animateContentSize(animationSpec = RowSizeSpec),
				// A description over the limit collapses to its icon — the shorter
				// ones render inline.
				infoAction = if (!state.pickerMode && item.description.isNotEmpty() && !ItemRow.showsDescription(item.description)) {
					{ InfoActionIcon { callbacks.onInfo(item) } }
				} else null,
				// References belong to the search view — a browse row lives in its
				// home, so there is nothing to jump to.
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
 * The rows' move springs, quick and without a bounce — not the stock
 * `StiffnessMediumLow` glide that `animateItem`/`animateContentSize`
 * default to.
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
	// Previews render [ItemUiModel] only and never reach the backing row, so the
	// payload stays at its default (`null`).
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
		// descriptions stay on the live setting's default — a preview must
		// not mutate process-global state other previews read
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
