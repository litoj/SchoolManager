package cz.litoj.schlmgr.ui.explorer.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What a long-press followed by a drag does on the explorer list. The list derives it
 * from its state: a selected list (or the picker) selects the dragged-over range, a
 * plain browsing list inside a container reorders, anything else ignores the drag.
 */
internal enum class ListDragMode { None, Select, Reorder }

/**
 * The row a long-press landed on and the drag mode the press started in, until (and
 * if) the gesture proves to be a drag. The row's `combinedClickable` reports the press
 * here; the list-level drag detector reads it. A plain holder, not Compose state: the
 * press must not trigger a recomposition.
 */
internal class PendingLongPress {
	var index: Int = -1
	var mode: ListDragMode = ListDragMode.None
}

/**
 * The single drag handler for the explorer list — the long-press-and-drag half only.
 * Taps and the long-press action itself stay with each row's `combinedClickable`; this
 * detector only extends an already-fired long-press into a drag. One detector on the
 * whole `LazyColumn` (never one per row: a row's long-press would consume the event the
 * drag detector needs) maps the finger to a row through [LazyListState.layoutInfo] and:
 *
 * - [ListDragMode.Select] selects the range from the pressed row to the finger,
 * - [ListDragMode.Reorder] moves the pressed row onto the finger's row as it crosses,
 *   and reports the final order through [onReorderDrop],
 * - [ListDragMode.None] consumes the drag without an effect.
 *
 * The mode is the one snapshotted at the long-press ([PendingLongPress.mode]), so the
 * selection change the press itself makes cannot flip a reorder into a select.
 *
 * The events are read in the `Initial` pass and consumed there while dragging, so the
 * list's own scroll never starts under the finger. Near the top/bottom edge an
 * auto-scroll loop keeps moving the list while the finger is held there.
 *
 * @param pending the row waiting on a long-press and its snapshotted mode
 * @param onSelectRange reports every drag-select step (anchor row, finger row)
 * @param onSelectEnd reports the end of a drag-select, after the last step
 * @param onReorderMove reports every live reorder step
 * @param onReorderDrop reports the end of a reorder drag
 */
@Composable
internal fun rememberListDragModifier(
	listState: LazyListState,
	pending: PendingLongPress,
	onSelectRange: (Int, Int) -> Unit,
	onSelectEnd: () -> Unit,
	onReorderMove: (Int, Int) -> Unit,
	onReorderDrop: () -> Unit,
): Modifier {
	val currentSelect by rememberUpdatedState(onSelectRange)
	val currentSelectEnd by rememberUpdatedState(onSelectEnd)
	val currentReorder by rememberUpdatedState(onReorderMove)
	val currentDrop by rememberUpdatedState(onReorderDrop)
	val scope = rememberCoroutineScope()
	val slop = LocalViewConfiguration.current.touchSlop
	val edge = with(LocalDensity.current) { EdgeZone.toPx() }
	val speed = with(LocalDensity.current) { ScrollStep.toPx() }

	return Modifier.pointerInput(listState) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
			var dragging = false
			var mode = ListDragMode.None
			var dragIndex = -1
			var pointerY = down.position.y
			var autoScroll: Job? = null

			fun applyDrag(y: Float) {
				val target = indexAtY(listState, y) ?: return
				when (mode) {
					ListDragMode.Select -> currentSelect(dragIndex, target)
					ListDragMode.Reorder -> if (target != dragIndex) {
						currentReorder(dragIndex, target)
						// the list already moved the row; follow its new slot
						dragIndex = target
					}
					ListDragMode.None -> {}
				}
			}

			while (true) {
				val event = awaitPointerEvent(PointerEventPass.Initial)
				val change = event.changes.firstOrNull { it.id == down.id } ?: break
				if (!change.pressed) break
				pointerY = change.position.y
				if (!dragging) {
					if (pending.index >= 0 && abs(change.position.y - down.position.y) > slop) {
						dragging = true
						mode = pending.mode
						dragIndex = pending.index
						pending.index = -1
						change.consume()
						if (mode != ListDragMode.None) {
							autoScroll = scope.launch {
								while (isActive) {
									val h = listState.layoutInfo.viewportSize.height.toFloat()
									val dy = when {
										pointerY < edge -> -speed
										pointerY > h - edge -> speed
										else -> 0f
									}
									if (dy != 0f) {
										listState.scrollBy(dy)
										applyDrag(pointerY)
									}
									delay(FrameMillis)
								}
							}
						}
					}
				} else {
					change.consume()
					applyDrag(pointerY)
				}
			}

			autoScroll?.cancel()
			pending.index = -1
			pending.mode = ListDragMode.None
			if (dragging && mode == ListDragMode.Select) currentSelectEnd()
			if (dragging && mode == ListDragMode.Reorder) currentDrop()
		}
	}
}

/** The row index at viewport-relative [y]; clamps to the first/last visible row. */
private fun indexAtY(listState: LazyListState, y: Float): Int? {
	val visible = listState.layoutInfo.visibleItemsInfo
	if (visible.isEmpty()) return null
	for (item in visible) {
		if (y >= item.offset && y < item.offset + item.size) return item.index
	}
	val first = visible.first()
	val last = visible.last()
	return when {
		y < first.offset -> first.index
		y >= last.offset + last.size -> last.index
		else -> null
	}
}

/** How close to an edge the finger must be before the list auto-scrolls. */
private val EdgeZone = 48.dp
/** Auto-scroll distance per frame. */
private val ScrollStep = 12.dp
/** ~60 fps. */
private const val FrameMillis = 16L
