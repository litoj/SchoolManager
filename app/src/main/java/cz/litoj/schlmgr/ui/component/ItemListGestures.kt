package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What a long-press followed by a drag does on the explorer list.
 * - a selected list (or the picker) selects the dragged-over range.
 * - a plain browsing list inside a container reorders.
 * - anything else ignores the drag.
 */
internal enum class ListDragMode { None, Select, Reorder }

internal data class RowGeometry(val index: Int, val offset: Int, val size: Int)

/**
 * The list's long-press-and-drag handler.
 * - Select drags the covered range; Reorder moves the pressed row; None consumes.
 * - mode snapshotted at the long-press; events in the `Initial` pass use edge auto-scroll.
 * - a structural list change mid-drag (a background reload landing) invalidates the claimed row indexes — the drop never fires.
 * - the content step keys the claim, so a selection remap or the drag's own reorder keeps the indexes valid.
 * - a reorder round trip — the row ends back at its own slot — never drops: the press reads as the selection the long-press started.
 */
@Composable
internal fun Modifier.rememberListDragModifier(
	listState: LazyListState,
	contentStep: Int,
	pending: LongPressDragHandler,
	onSelectRange: (Int, Int) -> Unit,
	onSelectEnd: () -> Unit,
	onReorderMove: (Int, Int) -> Unit,
	onReorderDrop: () -> Unit,
): Modifier {
	val currentSelect by rememberUpdatedState(onSelectRange)
	val currentSelectEnd by rememberUpdatedState(onSelectEnd)
	val currentReorder by rememberUpdatedState(onReorderMove)
	val currentDrop by rememberUpdatedState(onReorderDrop)
	// compared against the claim — a moved step means the list changed
	// structurally since the drag started
	val currentStep by rememberUpdatedState(contentStep)
	val scope = rememberCoroutineScope()
	val slop = LocalViewConfiguration.current.touchSlop
	val edge = with(LocalDensity.current) { EdgeZone.toPx() }
	val speed = with(LocalDensity.current) { ScrollStep.toPx() }

	return this.pointerInput(listState, edge, speed, slop) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
			var dragging = false
			var mode = ListDragMode.None
			var dragIndex = -1
			// the claimed slot — the drag's net move is measured against it
			var anchorIndex = -1
			var reported = false
			// the content step the drag's indexes belong to — a mismatch stops
			// every step and the drop
			var claimedStep = -1
			// Shared with the auto-scroll loop, so the access stays atomic.
			val pointerY = AtomicReference(down.position.y)
			var autoScroll: Job? = null

			fun stillValid() = claimedStep == currentStep

			fun stepSelect(y: Float) {
				if (!stillValid()) return
				indexAtY(listState, y, edge)?.let {
					currentSelect(dragIndex, it)
					reported = true
				}
			}

			fun stepReorder(y: Float) {
				if (!stillValid()) return
				val target = indexAtY(listState, y, edge) ?: return
				if (target != dragIndex) {
					// A reorder re-anchors the scroll on the first row, so put it back.
					val first = listState.firstVisibleItemIndex
					val offset = listState.firstVisibleItemScrollOffset
					currentReorder(dragIndex, target)
					reported = true
					// the list already moved the row, so follow its new slot
					dragIndex = target
					listState.requestScrollToItem(first, offset)
				}
			}

			while (true) {
				val event = awaitPointerEvent(PointerEventPass.Initial)
				// Another finger's event carries no change for the tracked one — skip it.
				val change = event.changes.firstOrNull { it.id == down.id } ?: continue
				if (!change.pressed) break
				pointerY.set(change.position.y)
				if (!dragging) {
					if (pending.isArmed && abs(change.position.y - down.position.y) > slop) {
						dragging = true
						val (anchor, claimedMode) = pending.claim() ?: break
						mode = claimedMode
						dragIndex = anchor
						anchorIndex = anchor
						claimedStep = currentStep
						change.consume()
						if (mode != ListDragMode.None) {
							autoScroll = scope.launch {
								while (isActive) {
									val y = pointerY.get()
									val h = listState.layoutInfo.viewportSize.height.toFloat()
									val dy = scrollStep(y, h, edge, speed)
									if (dy != 0f) {
										listState.scrollBy(dy)
										// the slots moved under the finger — step again
										if (mode == ListDragMode.Reorder) stepReorder(y)
										else if (mode == ListDragMode.Select) stepSelect(y)
									}
									// scrollBy applies the step at once and does not park the
									// coroutine, so the loop must wait out the frame itself —
									// without this it spins the main thread and locks the app.
									delay(FrameMillis)
								}
							}
						}
					}
				} else {
					change.consume()
					when (mode) {
						ListDragMode.Select -> stepSelect(pointerY.get())
						ListDragMode.Reorder -> stepReorder(pointerY.get())
						ListDragMode.None -> {}
					}
				}
			}

			autoScroll?.cancel()
			pending.reset()
			if (reported && stillValid()) {
				when (mode) {
					ListDragMode.Select -> currentSelectEnd()
					// a round trip back to the row's own slot is the
					// long-press's selection intent, not a reorder — the drop
					// (with its selection-ending persist) never fires
					ListDragMode.Reorder -> if (dragIndex != anchorIndex) currentDrop()
					ListDragMode.None -> {}
				}
			}
		}
	}
}

/**
 * The list's pull-at-top handler.
 * - a downward drag at the very top fires [onPull] once per gesture, past [TopPullThreshold].
 * - only a gesture that began at the top counts — a scroll from mid-list that drags past the top never fires.
 * - only the finger's own input counts — a fling or an auto-scroll never fires.
 * - consumes nothing, so the scroll and the stretch stay untouched.
 */
@Composable
internal fun Modifier.rememberTopPullModifier(
	listState: LazyListState,
	onPull: () -> Unit,
): Modifier {
	val currentPull by rememberUpdatedState(onPull)
	val threshold = with(LocalDensity.current) { TopPullThreshold.toPx() }
	// The gesture's start position decides whether a pull can count — the down
	// lands before any scroll moves the list, so the position read there is
	// the gesture's true start.
	var beganAtTop by remember { mutableStateOf(!listState.canScrollBackward) }
	return this
		.pointerInput(listState) {
			awaitEachGesture {
				awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
				beganAtTop = !listState.canScrollBackward
			}
		}
		.nestedScroll(
		remember(threshold) {
			object : NestedScrollConnection {
				private val pull = TopPullAccumulator(threshold)

				override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
					// UserInput covers the finger and the wheel — flings and programmatic scrolls never fire
					val pullable = !listState.canScrollBackward && beganAtTop
					if (pull.scroll(available.y, !pullable, source == NestedScrollSource.UserInput)) {
						currentPull()
					}
					return Offset.Zero
				}

				override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
					pull.gestureEnded()
					return Velocity.Zero
				}
			}
		}
	)
}

/**
 * The pull-at-top arithmetic behind [rememberTopPullModifier], free of [LazyListState]
 * so it stays unit-testable.
 * - sums the pull-down distance while the list cannot scroll back — a pull-down arrives as a positive delta.
 * - any scroll the other way, or room to scroll back, clears the pull.
 * - fires once per gesture, at the threshold.
 */
internal class TopPullAccumulator(private val threshold: Float) {
	private var pulled = 0f
	private var fired = false

	/**
	 * One scroll delta — [canScrollBackward] is the list's own backward room,
	 * [drag] marks the finger's own movement.
	 * @return true when the pull crossed the threshold just now.
	 */
	fun scroll(deltaY: Float, canScrollBackward: Boolean, drag: Boolean): Boolean {
		if (deltaY <= 0f || canScrollBackward) {
			pulled = 0f
			fired = false
			return false
		}
		if (fired || !drag) return false
		pulled += deltaY
		if (pulled < threshold) return false
		fired = true
		return true
	}

	/** The gesture ended — the next pull starts from zero. */
	fun gestureEnded() {
		pulled = 0f
		fired = false
	}
}

/**
 * The row index at a viewport-relative y in px — see the pure mapping below.
 *
 * @return row index, or null between rows and under a short list
 */
internal fun indexAtY(listState: LazyListState, y: Float, edgeZone: Float): Int? {
	val info = listState.layoutInfo
	return indexAtY(
		info.visibleItemsInfo.map { RowGeometry(it.index, it.offset, it.size) },
		y,
		info.viewportSize.height.toFloat(),
		edgeZone,
	)
}

/**
 * The pure finger-to-row mapping behind [indexAtY]: the row spanning `y`.
 * - above the viewport it is the first row — below the list, the last row
 *   only within the bottom edge zone (empty space and row gaps map to null).
 * - kept free of [LazyListState], so it is unit-testable without a list.
 */
internal fun indexAtY(
	visible: List<RowGeometry>,
	y: Float,
	viewportHeight: Float,
	edgeZone: Float,
): Int? {
	if (visible.isEmpty()) return null
	for (item in visible) {
		if (y >= item.offset && y < item.offset + item.size) return item.index
	}
	val first = visible.first()
	val last = visible.last()
	return when {
		y < 0f || y < first.offset -> first.index
		y > viewportHeight - edgeZone -> last.index
		else -> null
	}
}

/**
 * The auto-scroll step in px per frame for a finger at `y`.
 * - zero away from either edge, growing with depth into the edge zone
 *   up to a max step at the very edge. Upward steps are negative.
 * - on a viewport shorter than both zones the nearer edge wins, ties
 *   scroll up. Free of [LazyListState], so it is unit-testable.
 */
internal fun scrollStep(y: Float, viewportHeight: Float, edgeZone: Float, maxStep: Float): Float {
	val topActive = y < edgeZone
	val bottomActive = y > viewportHeight - edgeZone
	val useTop = when {
		topActive && bottomActive -> y <= viewportHeight / 2f
		topActive -> true
		bottomActive -> false
		else -> return 0f
	}
	val depth = if (useTop) (edgeZone - y) / edgeZone
		else (y - (viewportHeight - edgeZone)) / edgeZone
	val step = maxStep * depth.coerceIn(0f, 1f)
	return if (useTop) -step else step
}

private val EdgeZone = 48.dp
/** The auto-scroll step at the very edge. Inside [EdgeZone] it ramps up to this. */
private val ScrollStep = 8.dp
/** How long the auto-scroll loop waits between steps — one step per frame at 60 Hz. */
private const val FrameMillis = 16L
/** How far down the finger must pull at the very top before [rememberTopPullModifier] fires. */
private val TopPullThreshold = 40.dp
