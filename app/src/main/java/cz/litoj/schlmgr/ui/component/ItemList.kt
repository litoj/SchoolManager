package cz.litoj.schlmgr.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
	 * The closing tap of a double, the list consumes it and no row sees it.
	 * - the explorer flips every word and translation row to the side
	 * opposite the majority.
	 */
	val onItemDoubleTap: () -> Unit = {},
	/** The type icon of a row tapped, the host selects the row with it. */
	val onItemIconClick: (ItemUiModel) -> Unit = {},
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

private val DoubleTapSpan = 32.dp

/**
 * Classifies a tap as the second of a double by time and place.
 * - the place decides, since a flip may shrink the row and move another word under the finger.
 * - the previous tap must have hit a word, a chapter or note never opens a double.
 */
internal class DoubleTapDetector(private val spanPx: Float, private val windowMs: Long) {

	private var lastPosition = Offset.Zero
	private var lastAt = 0L
	private var lastWasWord = false

	/**
	 * Records a tap down and answers whether it closes a double.
	 * - the caller consumes the down, so no row sees the tap.
	 */
	fun onDown(position: Offset, at: Long, isWord: Boolean): Boolean {
		val closes = lastWasWord && at - lastAt <= windowMs &&
			(position - lastPosition).getDistance() <= spanPx
		lastPosition = position
		lastAt = at
		lastWasWord = isWord
		return closes
	}

	/** A content change must not pair taps from the list before it. */
	fun reset() {
		lastAt = 0
		lastWasWord = false
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
	val density = LocalDensity.current
	val pending = remember { LongPressDragHandler() }
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
	// the setting rebuilds the detector, its change applies to the next gesture
	val doubleTap = remember(density, ItemRow.doubleTapMs) {
		DoubleTapDetector(with(density) { DoubleTapSpan.toPx() }, ItemRow.doubleTapMs.toLong())
	}
	val onDoubleTap by rememberUpdatedState(callbacks.onItemDoubleTap)
	val selectionActive by rememberUpdatedState(state.selectionActive)
	// a structural change moves the rows, so a stale arm would act on the wrong ones.
	// - the key is the content step, since a selection remap keeps the arm the press just set.
	// - the taps of two different lists must not pair into a double.
	LaunchedEffect(state.contentStep) {
		pending.reset()
		doubleTap.reset()
	}
	val tapModifier = Modifier.pointerInput(doubleTap) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
			// the row under the tap decides the kind, a chapter or note
			// never opens a double
			val open = rowAt(listState, down.position.y)
			val onWord = open?.contentType == RowKind.WORD || open == null
			if (!selectionActive && doubleTap.onDown(down.position, down.uptimeMillis, onWord)) {
				// the list takes the second tap of a double, no row ever sees it
				down.consume()
				onDoubleTap()
			}
		}
	}
	LazyColumn(modifier.then(dragModifier).then(pullModifier).then(tapModifier), state = listState) {
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
				// the icon selects while a selection runs or the picker shows
				onIconClick = if (state.selectionActive || state.pickerMode) {
					{ callbacks.onItemIconClick(item) }
				} else null,
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
				// a reference row jumps to its home from wherever it shows
				referenceAction = if (!state.pickerMode && item.hasSource) {
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
 * Reveals the last row of the list whenever [ItemListState.scrollToBottom] asks.
 * - the reveal animates, so the rows it passes stay readable.
 * - the counter is consumed across recreations, so a rotation never replays
 *   a scroll that the import already asked for.
 */
@Composable
internal fun ListEndReveal(listState: ItemListState, scrollState: LazyListState) {
	var revealed by rememberSaveable { mutableIntStateOf(0) }
	LaunchedEffect(listState.scrollToBottom) {
		// a fresh view model restarts its counter, so the remembered level must follow
		if (listState.scrollToBottom < revealed) revealed = listState.scrollToBottom
		if (listState.scrollToBottom > revealed && listState.items.isNotEmpty()) {
			revealed = listState.scrollToBottom
			scrollState.animateScrollToItem(listState.items.lastIndex)
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

/** The visible row at the y position, or null over the empty part of the list. */
private fun rowAt(list: LazyListState, y: Float) =
	list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }

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
	ActionIcon(R.drawable.ic_ref, onClick, R.string.more_go_to)
}

@Composable
private fun ActionIcon(res: Int, onClick: () -> Unit, description: Int? = null) {
	val label = if (description != null) stringResource(description) else null
	Image(
		painterResource(res),
		contentDescription = label,
		modifier = Modifier
			.size(IconSize)
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
