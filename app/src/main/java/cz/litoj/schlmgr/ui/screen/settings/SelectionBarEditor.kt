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
 * The editor of the selection actions.
 * - a long-press drag reorders every action, where the first four of a list sit on its strip.
 * - the placement marker names the strips that show an action, so no hidden takeover exists.
 */
@Composable
internal fun SelectionBarEditor(state: SettingsState) {
	var dragging by remember { mutableStateOf<SelectionAction?>(null) }
	var fingerY by remember { mutableFloatStateOf(0f) }
	var grabOffset by remember { mutableFloatStateOf(0f) }
	val rowBounds = remember { mutableMapOf<Int, Rect>() }
	val haptic = LocalHapticFeedback.current
	val browseBar = state.selectionOrder.filter { it.applies(false) }
		.take(ActionConfig.BAR_SLOTS).toSet()
	val searchBar = state.selectionOrder.filter { it.applies(true) }
		.take(ActionConfig.BAR_SLOTS).toSet()

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
		// the list moved nothing during the drag, so the drop lands the one move
		val from = state.selectionOrder.indexOf(dragged)
		val to = insertAt(dragged)
		if (to != from) state.moveSelectionAction(from, to)
		dragging = null
	}

	fun startDrag(action: SelectionAction, yInRow: Float) {
		val index = state.selectionOrder.indexOf(action)
		dragging = action
		grabOffset = yInRow
		fingerY = (rowBounds[index]?.top ?: 0f) + yInRow
		haptic.performHapticFeedback(HapticFeedbackType.LongPress)
	}

	val dragged = dragging
	val lineBeforeIndex = dragged?.let(::lineBefore)
	val lineAtEnd = dragged != null && lineBeforeIndex == null

	Box(Modifier.fillMaxWidth()) {
		Column(Modifier.fillMaxWidth()) {
			state.selectionOrder.forEachIndexed { index, action ->
				if (lineBeforeIndex == index) InsertionLine()
				key(action) {
					ActionSettingRow(
						action = action,
						tint = if (action in browseBar || action in searchBar)
							colorResource(R.color.colorSelectedRow) else Color.Transparent,
						placement = placementOf(action, browseBar, searchBar),
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
		}
	}
}

/** The strips that show one action, which the row names at its end. */
@Composable
private fun placementOf(
	action: SelectionAction,
	browseBar: Set<SelectionAction>,
	searchBar: Set<SelectionAction>,
): String = stringResource(
	when {
		action in browseBar && action in searchBar -> R.string.setts_place_both
		action in browseBar -> R.string.setts_place_browse
		action in searchBar -> R.string.setts_place_search
		else -> R.string.setts_place_more
	},
)

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
	placement: String,
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
		}
		Text(
			placement,
			Modifier.padding(start = 5.dp, end = 5.dp),
			style = TextStyle(fontSize = 11.sp, color = DefaultTextColor),
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

/** A hairline under the action rows. */
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
