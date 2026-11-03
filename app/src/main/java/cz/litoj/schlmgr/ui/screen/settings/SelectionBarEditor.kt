package cz.litoj.schlmgr.ui.screen.settings

import androidx.annotation.ColorRes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.component.IconSize
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.description
import cz.litoj.schlmgr.ui.viewmodel.icon
import cz.litoj.schlmgr.ui.viewmodel.label

/**
 * The editor of the selection strip: the ordered actions with the connectable Go-to.
 * - a long-press drag reorders, the first four rows carry the strip tint.
 * - dragging Go-to onto one of the first four connects it, a drop elsewhere releases it.
 */
@Composable
internal fun SelectionBarEditor(state: SettingsState) {
	var dragging by remember { mutableStateOf<SelectionAction?>(null) }
	var fingerY by remember { mutableFloatStateOf(0f) }
	var grabOffset by remember { mutableFloatStateOf(0f) }
	// the finger reads in the editor's own coordinates, so a scrolled host never skews the marker
	var localFingerX by remember { mutableFloatStateOf(0f) }
	var localFingerY by remember { mutableFloatStateOf(0f) }
	val rowBounds = remember { mutableMapOf<Int, Rect>() }
	val haptic = LocalHapticFeedback.current
	val connectDrag = dragging?.connectable == true

	fun middleOf(index: Int): Float =
		rowBounds[index]?.let { (it.top + it.bottom) / 2f } ?: 0f

	/** The floating row sits at the finger, keeping the grabbed spot under it. */
	fun draggedOffset(action: SelectionAction): Float {
		val slot = state.selectionOrder.indexOf(action).takeIf { it >= 0 } ?: return 0f
		val top = rowBounds[slot]?.top ?: return 0f
		return fingerY - grabOffset - top
	}

	/** The insertion slot among the other rows, where the finger passed their middles. */
	fun insertAt(dragged: SelectionAction): Int {
		val from = state.selectionOrder.indexOf(dragged)
		return state.selectionOrder.indices.count { it != from && middleOf(it) < fingerY }
	}

	/** The row the insertion line stands before, null for after the last row. */
	fun lineBefore(dragged: SelectionAction): Int? {
		val from = state.selectionOrder.indexOf(dragged)
		return state.selectionOrder.indices.firstOrNull { it != from && middleOf(it) >= fingerY }
	}

	fun endDrag() {
		val dragged = dragging ?: return
		if (dragged.connectable) {
			// only a strip slot takes the connection, so the drop elsewhere releases it
			val slot = rowBounds.entries
				.firstOrNull { fingerY >= it.value.top && fingerY < it.value.bottom }
				?.key
				?.takeIf { it in 0 until ActionConfig.BAR_SLOTS }
			state.connectGoto(slot?.let(state.selectionOrder::get))
		} else {
			// the list moved nothing during the drag, so the drop lands the one move
			val from = state.selectionOrder.indexOf(dragged)
			val to = insertAt(dragged)
			if (to != from) state.moveSelectionAction(from, to)
		}
		dragging = null
	}

	fun startGoto(yInRow: Float) {
		dragging = SelectionAction.GOTO
		grabOffset = yInRow
		fingerY = (rowBounds[GOTO_ROW]?.top ?: 0f) + yInRow
		haptic.performHapticFeedback(HapticFeedbackType.LongPress)
	}

	fun startDrag(action: SelectionAction, yInRow: Float) {
		// the slot resolves live, since a reorder moved the row since its composition
		val index = state.selectionOrder.indexOf(action)
		dragging = action
		grabOffset = yInRow
		fingerY = (rowBounds[index]?.top ?: 0f) + yInRow
		haptic.performHapticFeedback(HapticFeedbackType.LongPress)
	}

	val dragged = dragging
	val lineBeforeIndex = if (dragged != null && !dragged.connectable) lineBefore(dragged) else null
	val lineAtEnd = dragged != null && !dragged.connectable && lineBeforeIndex == null

	Box(
		Modifier
			.fillMaxWidth()
			// the initial pass sees the finger before the dragged row consumes it
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
						localFingerX = change.position.x
						localFingerY = change.position.y
					}
				}
			},
	) {
		Column(Modifier.fillMaxWidth()) {
			state.selectionOrder.forEachIndexed { index, action ->
				// the strip ends after the fourth slot, and the connectable row follows a divider
				if (index == ActionConfig.BAR_SLOTS) Divider(R.color.colorDivider)
				if (lineBeforeIndex == index) InsertionLine()
				key(action) {
					ActionSettingRow(
						action = action,
						tint = when {
							connectDrag && rowBounds.entries.firstOrNull { fingerY >= it.value.top && fingerY < it.value.bottom }?.key == index ->
								colorResource(R.color.colorGroupBackground)
							// the strip slots wear the primary wash, the rest stay plain
							index < ActionConfig.BAR_SLOTS -> colorResource(R.color.colorSelectedRow)
							else -> Color.Transparent
						},
						boundGoto = state.connections[SelectionAction.GOTO] == action,
						tag = "action_${action.name}",
						dragged = dragging == action,
						offset = if (dragging == action) ({ draggedOffset(action) }) else ({ 0f }),
						onBounds = { rowBounds[index] = it },
						onDragStart = { y -> startDrag(action, y) },
						onDrag = { dy -> fingerY += dy },
						onDragEnd = ::endDrag,
						onDragCancel = { dragging = null },
					)
				}
			}
			if (lineAtEnd) InsertionLine()
			Divider(R.color.colorDivider)
			val bound = state.connections[SelectionAction.GOTO]
			key(SelectionAction.GOTO) {
				ActionSettingRow(
					action = SelectionAction.GOTO,
					tint = if (connectDrag) colorResource(R.color.colorSubBackground) else Color.Transparent,
					tag = "action_${SelectionAction.GOTO.name}",
					dragged = dragging == SelectionAction.GOTO,
					offset = { 0f },
					onBounds = { rowBounds[GOTO_ROW] = it },
					onDragStart = { y -> startGoto(y) },
					onDrag = { dy -> fingerY += dy },
					onDragEnd = ::endDrag,
					onDragCancel = { dragging = null },
					extra = {
						Text(
							stringResource(R.string.setts_goto_connect),
							style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
						)
						// the connection state reads as a fact under the hint
						Text(
							stringResource(
								if (bound == null) R.string.setts_goto_loose else R.string.setts_goto_bound,
								if (bound == null) "" else stringResource(bound.label),
							),
							style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
						)
					},
				)
			}
		}
		if (dragging == SelectionAction.GOTO) GotoMarker({ localFingerX }, { localFingerY })
	}
}

/** One action row, with the drag gesture and the strip presentation. */
@Composable
private fun ActionSettingRow(
	action: SelectionAction,
	tint: Color,
	tag: String,
	dragged: Boolean,
	offset: () -> Float,
	onBounds: (Rect) -> Unit,
	onDragStart: (Float) -> Unit,
	onDrag: (Float) -> Unit,
	onDragEnd: () -> Unit,
	onDragCancel: () -> Unit,
	boundGoto: Boolean = false,
	extra: (@Composable () -> Unit)? = null,
) {
	// the row keeps its gesture handler across reorders, so the callbacks read current state
	val currentStart by rememberUpdatedState(onDragStart)
	val currentDrag by rememberUpdatedState(onDrag)
	val currentEnd by rememberUpdatedState(onDragEnd)
	val currentCancel by rememberUpdatedState(onDragCancel)
	Row(
		Modifier
			.fillMaxWidth()
			.testTag(tag)
			// positionInRoot stays unclipped, so a row scrolled out still reports its place
			.onGloballyPositioned { onBounds(Rect(0f, it.positionInRoot().y, 1f, it.positionInRoot().y + it.size.height)) }
			// the dragged row draws over every sibling and rides the finger
			.zIndex(if (dragged) 1f else 0f)
			.graphicsLayer {
				translationY = offset()
			}
			// the dragged row hides the rows it passes, the slot tint sits over that
			.background(if (dragged) colorResource(R.color.colorPrimaryBg) else Color.Transparent)
			.background(tint)
			.pointerInput(action) {
				detectDragGesturesAfterLongPress(
					onDragStart = { position -> currentStart(position.y) },
					onDrag = { change, amount ->
						change.consume()
						currentDrag(amount.y)
					},
					onDragEnd = { currentEnd() },
					onDragCancel = { currentCancel() },
				)
			}
			.padding(horizontal = 5.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Image(
			painterResource(action.icon),
			contentDescription = null,
			// the icon follows the row text, so no accent makes it faint
			colorFilter = ColorFilter.tint(DefaultTextColor),
			modifier = Modifier.size(IconSize),
		)
		Column(
			Modifier
				.weight(1f)
				.padding(start = 5.dp),
		) {
			Text(
				stringResource(action.label),
				style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
			)
			Text(
				stringResource(action.description),
				style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
			)
			extra?.invoke()
		}
		if (boundGoto) {
			// the connection marker shows the search takeover from the target side too
			Column(
				Modifier.padding(end = 8.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Image(
					painterResource(R.drawable.ic_goto),
					contentDescription = null,
					// the untinted drawable is a light green that washes out on the row
					colorFilter = ColorFilter.tint(DefaultTextColor),
					modifier = Modifier.size(IconSize),
				)
				Text(
					stringResource(R.string.setts_bound_marker),
					style = TextStyle(fontSize = 10.sp, color = DefaultTextColor),
				)
			}
		}
	}
}

/**
 * The marker that rides the finger during a Go-to drag.
 * - it stays centred under the finger in both axes, so the target keeps its merge highlight.
 */
@Composable
private fun GotoMarker(fingerX: () -> Float, fingerY: () -> Float) {
	Box(
		Modifier
			.testTag("goto_marker")
			// the layer reads the finger, so a move never recomposes the editor
			.graphicsLayer {
				translationX = fingerX() - GotoMarkerSize.toPx() / 2f
				translationY = fingerY() - GotoMarkerSize.toPx() / 2f
			}
			.zIndex(1f)
			.background(colorResource(R.color.colorPrimaryBg))
			.size(GotoMarkerSize),
		contentAlignment = Alignment.Center,
	) {
		Image(
			painterResource(SelectionAction.GOTO.icon),
			contentDescription = null,
			colorFilter = ColorFilter.tint(DefaultTextColor),
			modifier = Modifier.size(IconSize),
		)
	}
}

/** The drop marker: where the dragged row lands on release. */
@Composable
private fun InsertionLine() {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 8.dp)
			.background(colorResource(R.color.colorPrimaryHeader))
			.height(2.dp),
	)
}

/** A hairline between the strip, the overflow and the connectable row. */
@Composable
private fun Divider(@ColorRes color: Int) {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 8.dp)
			.background(colorResource(color))
			.height(1.dp),
	)
}

/** The bounds key of the Go-to row, which no drop ever targets. */
private const val GOTO_ROW = -1

/** The finger marker of a Go-to drag, sized to stay clear of the target's highlight. */
private val GotoMarkerSize = 36.dp
