package cz.litoj.schlmgr.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.BareTextField
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.IconSize
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import cz.litoj.schlmgr.ui.component.SettingSwitchRow
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.description
import cz.litoj.schlmgr.ui.viewmodel.icon
import cz.litoj.schlmgr.ui.viewmodel.label
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

private val HeaderBackground @Composable get() = colorResource(R.color.colorSectionHeader)

/**
 * The settings screen: the top bar above the settings list.
 * - the list holds the interface switches, the export splitter field and the test defaults.
 * - every edit applies and persists at once through [SettingsState].
 *
 * @param onBack the back arrow press, the host owns the back stack
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(up = true, onNavigate = onBack)
			TopBarTitle(stringResource(R.string.menu_settings))
		}
		SettingsList(viewModel.state, Modifier.weight(1f).headerShadow())
	}
}

@Composable
private fun SettingsList(state: SettingsState, modifier: Modifier = Modifier) {
	val keyboard = LocalSoftwareKeyboardController.current
	Column(
		modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState()),
	) {
		SectionHeader(stringResource(R.string.setts_interface))
		SettingSwitchRow(stringResource(R.string.setts_him_parse), state.parse, state::toggleParse)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped), state.defFlip, state::toggleDefFlip)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped_search), state.searchDefFlip, state::toggleSearchDefFlip)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_desc_lines), state.descLinesText, state::setDescLines,
		) { state.descLinesFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_double_tap_ms), state.doubleTapMsText, state::setDoubleTapMs,
		) { state.doubleTapMsFocusLost(); keyboard?.hide() }
		HairlineDivider()
		WordSplitRow(state)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_search_diacritics), state.searchDiacritics, state::toggleSearchDiacritics)
		// no resource exists for the Test section label, so it stays a literal
		SectionHeader(stringResource(R.string.setts_selection_bar))
		Text(
			stringResource(R.string.setts_bar_hint),
			Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
			style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
		)
		SelectionBarEditor(state)
		SectionHeader("Test")
		SettingNumberRow(
			stringResource(R.string.setts_test_amount), state.amountText, state::setAmount,
		) { state.amountFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_test_time), state.timeText, state::setTime,
		) { state.timeFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_test_clever), state.clever, state::toggleClever)
	}
}

@Composable
private fun SectionHeader(title: String) {
	Text(
		title,
		Modifier
			.fillMaxWidth()
			.background(HeaderBackground)
			.padding(horizontal = 15.dp, vertical = 2.dp),
		style = TextStyle(fontSize = 15.sp, color = DefaultTextColor),
	)
}

@Composable
private fun WordSplitRow(state: SettingsState) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			stringResource(R.string.setts_word_splitter),
			Modifier.weight(1f),
			style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
		)
		BareTextField(
			value = state.wordSplitText,
			onValueChange = state::setWordSplit,
			fontSize = 18.sp,
			onFocusLost = state::wordSplitFocusLost,
		)
	}
}

/**
 * The editor of the selection strip: the ordered actions with the connectable Go-to.
 * - a long-press drag reorders, the first four rows carry the strip tint.
 * - dragging Go-to onto one of the first four connects it, a drop elsewhere releases it.
 */
@Composable
private fun SelectionBarEditor(state: SettingsState) {
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
					index < ActionConfig.BAR_SLOTS -> colorResource(R.color.colorSubBackground)
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
			},
			trailing = {
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
	trailing: (@Composable () -> Unit)? = null,
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
					modifier = Modifier.size(IconSize),
				)
				Text(
					stringResource(R.string.setts_bound_marker),
					style = TextStyle(fontSize = 10.sp, color = DefaultTextColor),
				)
			}
		}
		trailing?.invoke()
	}
}

/** The bounds key of the Go-to row, which no drop ever targets. */
private const val GOTO_ROW = -1


