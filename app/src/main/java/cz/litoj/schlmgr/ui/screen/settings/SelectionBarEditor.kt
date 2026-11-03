package cz.litoj.schlmgr.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
	var pressY by remember { mutableFloatStateOf(0f) }
	var dragOffset by remember { mutableFloatStateOf(0f) }
	val rowBounds = remember { mutableMapOf<Int, Rect>() }
	val connectDrag = dragging?.connectable == true

	fun hoveredIndex(): Int? {
		val y = pressY + dragOffset
		return rowBounds.entries.firstOrNull { y >= it.value.top && y < it.value.bottom }?.key
	}

	fun endDrag() {
		val dragged = dragging ?: return
		val hovered = hoveredIndex()
		if (dragged.connectable) {
			// only a strip slot takes the connection, so the drop elsewhere releases it
			state.connectGoto(hovered?.takeIf { it < ActionConfig.BAR_SLOTS }?.let(state.selectionOrder::get))
		} else if (hovered != null) {
			val from = state.selectionOrder.indexOf(dragged)
			if (hovered != from) state.moveSelectionAction(from, hovered)
		}
		dragging = null
		dragOffset = 0f
	}

	fun startDrag(action: SelectionAction, index: Int, y: Float) {
		dragging = action
		dragOffset = 0f
		pressY = (rowBounds[index]?.top ?: 0f) + y
	}

	Column(Modifier.fillMaxWidth()) {
		state.selectionOrder.forEachIndexed { index, action ->
			val hovered = hoveredIndex()
			ActionSettingRow(
				action = action,
				index = index,
				tint = when {
					dragging != null && hovered == index -> colorResource(R.color.colorGroupBackground)
					// the strip slots wear the primary wash, the rest stay plain
					index < ActionConfig.BAR_SLOTS -> colorResource(R.color.colorSelectedRow)
					else -> Color.Transparent
				},
				dragged = dragging == action,
				offset = if (dragging == action) ({ dragOffset }) else ({ 0f }),
				boundGoto = state.connections[SelectionAction.GOTO] == action,
				onBounds = { rowBounds[index] = it },
				onDragStart = { y -> startDrag(action, index, y) },
				onDrag = { dy -> dragOffset += dy },
				onDragEnd = ::endDrag,
				onDragCancel = { dragging = null; dragOffset = 0f },
			)
		}
		val bound = state.connections[SelectionAction.GOTO]
		ActionSettingRow(
			action = SelectionAction.GOTO,
			index = GOTO_ROW,
			tint = if (dragging?.connectable == true) colorResource(R.color.colorSubBackground) else Color.Transparent,
			dragged = dragging == SelectionAction.GOTO,
			offset = if (dragging == SelectionAction.GOTO) ({ dragOffset }) else ({ 0f }),
			onBounds = { rowBounds[GOTO_ROW] = it },
			onDragStart = { y -> startDrag(SelectionAction.GOTO, GOTO_ROW, y) },
			onDrag = { dy -> dragOffset += dy },
			onDragEnd = ::endDrag,
			onDragCancel = { dragging = null; dragOffset = 0f },
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

/** One action row, with the drag gesture and the strip presentation. */
@Composable
private fun ActionSettingRow(
	action: SelectionAction,
	index: Int,
	tint: Color,
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
	Row(
		Modifier
			.fillMaxWidth()
			.onGloballyPositioned { onBounds(it.boundsInRoot()) }
			.graphicsLayer {
				translationY = offset()
				alpha = if (dragged) 0.7f else 1f
			}
			.background(tint)
			.pointerInput(index) {
				detectDragGesturesAfterLongPress(
					onDragStart = { position -> onDragStart(position.y) },
					onDrag = { change, amount ->
						change.consume()
						onDrag(amount.y)
					},
					onDragEnd = onDragEnd,
					onDragCancel = onDragCancel,
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

/** The bounds key of the Go-to row, which no drop ever targets. */
private const val GOTO_ROW = -1


