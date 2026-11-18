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
 * - an unselected row selects the range the drag covers.
 * - a selected row moves every selected row where the list has an order.
 * - none sits before the press claims its mode.
 */
internal enum class ListDragMode { None, Select, Reorder }

internal data class RowGeometry(val index: Int, val offset: Int, val size: Int)

/**
 * The long-press-and-drag handler of the list.
 * - a select drag covers the range, reorder gathers every selected row, none consumes.
 * - a reorder swaps only past the middle of the target row, so a held finger cannot undo a swap.
 * - the mode is snapshotted at the long-press, and events in the Initial pass use edge auto-scroll.
 * - a structural list change mid-drag invalidates the claimed row indexes, so the drop never fires.
 * - the content step keys the claim, so a selection remap or a drag reorder keeps the indexes.
 * - a reorder round trip keeps its order and never drops.
 */
@Composable
internal fun Modifier.rememberListDragModifier(
	listState: LazyListState,
	contentStep: Int,
	pending: LongPressDragHandler,
	selectedOf: (Int) -> List<Int>,
	onSelectRange: (Int, Int) -> Unit,
	onSelectEnd: () -> Unit,
	onGestureEnd: () -> Unit,
	onReorderStart: (Int, Offset) -> Unit,
	onReorderStep: (Int, Offset, Int?) -> Unit,
	onReorderDrop: (Int, Int?) -> Unit,
): Modifier {
	val currentSelect by rememberUpdatedState(onSelectRange)
	val currentSelectEnd by rememberUpdatedState(onSelectEnd)
	val currentGestureEnd by rememberUpdatedState(onGestureEnd)
	val currentStart by rememberUpdatedState(onReorderStart)
	val currentReorderStep by rememberUpdatedState(onReorderStep)
	val currentDrop by rememberUpdatedState(onReorderDrop)
	val currentSelected by rememberUpdatedState(selectedOf)
	// a moved step against the claim means the list changed structurally during the drag
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
			// the claimed slot, which measures the net move of the drag
			var anchorIndex = -1
			// every selected row the press gathers and moves as one run
			var dragRows = emptyList<Int>()
			var reported = false
			// the content step the drag indexes belong to.
			// a mismatch stops every step and the drop
			var claimedStep = -1
			// where the drop would land, null before a step
			var pendingTarget: Int? = null
			// shared with the auto-scroll loop, so the access stays atomic.
			val pointer = AtomicReference(down.position)
			var autoScroll: Job? = null

			fun stillValid() = claimedStep == currentStep

			fun stepSelect(y: Float) {
				if (!stillValid()) return
				indexAtY(listState, y, edge)?.let {
					currentSelect(dragIndex, it)
					reported = true
				}
			}

			fun stepReorder(position: Offset) {
				if (!stillValid()) return
				// the list stays put until the drop, so the insert slot counts the unselected rows
				pendingTarget = insertIndex(listState, dragRows, position.y)
				reported = true
				currentReorderStep(anchorIndex, position, pendingTarget)
			}

			while (true) {
				val event = awaitPointerEvent(PointerEventPass.Initial)
				// another finger carries no change for the tracked one, so skip it.
				val change = event.changes.firstOrNull { it.id == down.id } ?: continue
				if (!change.pressed) break
				pointer.set(change.position)
				if (!dragging) {
					if (pending.isArmed && abs(change.position.y - down.position.y) > slop) {
						dragging = true
						val (anchor, claimedMode) = pending.claim() ?: break
						mode = claimedMode
						dragIndex = anchor
						anchorIndex = anchor
						dragRows = currentSelected(anchor)
						claimedStep = currentStep
						change.consume()
						if (mode == ListDragMode.Reorder) currentStart(anchorIndex, down.position)
						if (mode != ListDragMode.None) {
							autoScroll = scope.launch {
								while (isActive) {
									val position = pointer.get()
									val y = position.y
									val h = listState.layoutInfo.viewportSize.height.toFloat()
									val dy = scrollStep(y, h, edge, speed)
									if (dy != 0f) {
										listState.scrollBy(dy)
										// the slots moved under the finger, so step again
										if (mode == ListDragMode.Reorder) stepReorder(position)
										else if (mode == ListDragMode.Select) stepSelect(y)
									}
									// scrollBy does not park the coroutine, so wait out the frame.
									// without this it spins the main thread and locks the app.
									delay(FrameMillis)
								}
							}
						}
					}
				} else {
					change.consume()
					when (mode) {
						ListDragMode.Select -> stepSelect(pointer.get().y)
						ListDragMode.Reorder -> stepReorder(pointer.get())
						ListDragMode.None -> {}
					}
				}
			}

			autoScroll?.cancel()
			pending.reset()
			if (reported && stillValid()) {
				when (mode) {
					ListDragMode.Select -> currentSelectEnd()
					// a round trip back to its own slot is the selection intent of the long-press.
					// it is not a reorder, so the drop with its persist never fires.
					ListDragMode.Reorder -> {
						currentDrop(anchorIndex, pendingTarget)
					}
					ListDragMode.None -> {}
				}
			}
			currentGestureEnd()
		}
	}
}

/**
 * The pull-at-top handler of the list.
 * - a downward drag at the very top fires [onPull] once per gesture, past [TopPullThreshold].
 * - only a gesture that began at the top counts, so a scroll from mid-list never fires.
 * - only the own input of the finger counts, so a fling or an auto-scroll never fires.
 * - consumes nothing, so the scroll and the stretch stay untouched.
 */
@Composable
internal fun Modifier.rememberTopPullModifier(
	listState: LazyListState,
	onPull: () -> Unit,
): Modifier {
	val currentPull by rememberUpdatedState(onPull)
	val threshold = with(LocalDensity.current) { TopPullThreshold.toPx() }
	// the start position decides whether a pull can count.
	// the down lands before any scroll moves the list, so the position read is the true start.
	var beganAtTop by remember { mutableStateOf(!listState.canScrollBackward) }
	// a wheel gesture has no down, so its first tick is its own gesture start.
	// the scrollable clears the flag at every gesture end, so a wheel tick
	// never rides a finger flag
	var finger by remember { mutableStateOf(false) }
	return this
		.pointerInput(listState) {
			awaitEachGesture {
				awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
				finger = true
				beganAtTop = !listState.canScrollBackward
			}
		}
		.nestedScroll(
		remember(threshold) {
			object : NestedScrollConnection {
				private val pull = TopPullAccumulator(threshold)

				override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
					// the user input source covers the finger and the wheel.
					// flings and programmatic scrolls never fire.
					if (!finger) beganAtTop = !listState.canScrollBackward
					val pullable = !listState.canScrollBackward && beganAtTop
					if (pull.scroll(available.y, !pullable, source == NestedScrollSource.UserInput)) {
						currentPull()
					}
					return Offset.Zero
				}

				override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
					pull.gestureEnded()
					finger = false
					return Velocity.Zero
				}
			}
		}
	)
}

/**
 * The pull-at-top arithmetic behind [rememberTopPullModifier], free of [LazyListState]
 * so it stays unit-testable.
 * - sums the pull-down distance while the list cannot scroll back, where a pull-down is positive.
 * - any scroll the other way, or room to scroll back, clears the pull.
 * - fires once per gesture, at the threshold.
 */
internal class TopPullAccumulator(private val threshold: Float) {
	private var pulled = 0f
	private var fired = false

	/**
	 * True when the pull crosses the threshold just now.
	 * - [canScrollBackward] is the backward room of the list.
	 * - [drag] marks the own movement of the finger.
	 */
	fun scroll(deltaY: Float, canScrollBackward: Boolean, drag: Boolean): Boolean {
		if (deltaY <= 0f || canScrollBackward) {
			pulled = 0f
			return false
		}
		if (fired || !drag) return false
		pulled += deltaY
		if (pulled < threshold) return false
		fired = true
		return true
	}

	fun gestureEnded() {
		pulled = 0f
		fired = false
	}
}

/**
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
 * The pure finger-to-row mapping behind [indexAtY]: the row spanning y.
 * - above the viewport it is the first row, and below the list the last row.
 * - the last row counts only within the bottom edge zone.
 * - empty space and row gaps map to null.
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
 * The insertion slot of the moved [selected] rows at finger [y].
 * - the dropped selection lands before the row at that slot of the remaining list.
 * - only unselected rows count, since every selected row leaves its slot.
 * - the rows above the viewport count too.
 * - free of [LazyListState], so it is unit-testable.
 */
internal fun insertIndex(listState: LazyListState, selected: List<Int>, y: Float): Int {
	val info = listState.layoutInfo
	return insertIndexAt(
		info.visibleItemsInfo.map { RowGeometry(it.index, it.offset, it.size) },
		selected,
		y,
	)
}

internal fun insertIndexAt(visible: List<RowGeometry>, selected: List<Int>, y: Float): Int {
	if (visible.isEmpty()) return 0
	val moving = selected.toSet()
	val first = visible.first().index
	var count = (0 until first).count { it !in moving }
	count += visible.count { row ->
		row.index !in moving && row.offset + row.size / 2f <= y
	}
	return count
}

/**
 * The auto-scroll step in px per frame for a finger at y.
 * - zero away from either edge, growing with depth into the edge zone.
 * - the step reaches its maximum at the very edge, and upward steps are negative.
 * - on a viewport shorter than both zones the nearer edge wins, and ties scroll up.
 * - free of [LazyListState], so it is unit-testable.
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
	// the squared ramp keeps the band's outer half gentle and the edge fast
	val step = maxStep * depth.coerceIn(0f, 1f).let { it * it }
	return if (useTop) -step else step
}

private val EdgeZone = 120.dp
/** The auto-scroll step at the very edge. Inside [EdgeZone] it ramps up to this. */
private val ScrollStep = 12.dp
/** How long the auto-scroll loop waits between steps: one step per frame at 60 Hz. */
private const val FrameMillis = 16L
private val TopPullThreshold = 40.dp
