package cz.litoj.schlmgr.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R

/**
 * The app's standard single-line text field, replicating the old bare `EditText` rows:
 * no container, no Material paddings — just the text (at the same size as the surrounding
 * labels), an accent cursor and a thin gray bottom line that marks the field as editable,
 * turning green (the app accent) while focused.
 *
 * The field's minimum width equals its rendered height, so it never renders narrower
 * than a square box even when empty.
 *
 * The value-based BasicTextField overload was removed in this Compose version, so the
 * state-based API is used (see CreatorPopup.BorderedTextField): the incoming [value] is
 * mirrored into the TextFieldState and edits are reported back through [onValueChange].
 *
 * Optional escapes for callers that need a full-row field (e.g. the test answer rows):
 * [fillWidth] skips the square clamp and lets the field take the caller's width (plus a
 * comfortable minimum touch height), [focusRequester] exposes the inner field for focus
 * chains, and [imeAction] sets the keyboard action key explicitly. All default off, so
 * the compact callers (settings, test setup) are unaffected.
 */
@Composable
fun BareTextField(
	value: String,
	onValueChange: (String) -> Unit,
	fontSize: TextUnit,
	modifier: Modifier = Modifier,
	keyboardType: KeyboardType = KeyboardType.Text,
	hint: String? = null,
	textColor: Color = DefaultTextColor,
	fillWidth: Boolean = false,
	focusRequester: FocusRequester? = null,
	imeAction: ImeAction? = null,
	onFocusLost: (() -> Unit)? = null,
	onImeAction: (() -> Unit)? = null,
) {
	var focused by remember { mutableStateOf(false) }
	val state = rememberTextFieldState(value)
	if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
	LaunchedEffect(state) {
		snapshotFlow { state.text.toString() }.collect(onValueChange)
	}
	val style = TextStyle(fontSize = fontSize, color = textColor)
	// theme colors captured here: the draw scope below cannot read them itself
	val accent = colorResource(R.color.colorPrimaryHeader)
	val idleBar = colorResource(R.color.colorControlLine)
	Box(
		modifier
			// The old EditText's thin bottom line: gray so the field is visible as editable,
			// accent green while focused. Kept BEFORE the square-clamp layout below: draw
			// modifiers further along the chain draw in the inner (pre-clamp) coordinate
			// space, so ordering it before makes the line span the full clamped width.
			.drawBehind {
				val y = size.height - 1.dp.toPx()
				drawLine(
					color = if (focused) accent else idleBar,
					start = Offset(0f, y),
					end = Offset(size.width, y),
					strokeWidth = 1.dp.toPx(),
				)
			}
			// Compact default: a one-line field should never render narrower than it is
			// tall — the minimum width is clamped to the rendered height in the same
			// layout pass (no measure-then-recompose round-trip), so the field is at
			// least a square box. Full-width callers instead just get a comfortable
			// minimum touch height; the caller's width (e.g. fillMaxWidth) applies as-is.
			.then(
				if (fillWidth) Modifier.defaultMinSize(minHeight = MinTouchHeight)
				else Modifier.layout { measurable, constraints ->
					val placeable = measurable.measure(constraints)
					val minHeight = placeable.height
					val minWidth = maxOf(placeable.width, minHeight)
					layout(minWidth, minHeight) { placeable.place(0, 0) }
				},
			)
			.onFocusChanged {
				focused = it.isFocused
				if (!it.isFocused) onFocusLost?.invoke()
			},
		contentAlignment = Alignment.CenterStart,
	) {
		if (state.text.isEmpty() && hint != null) {
			Text(hint, style = style.copy(color = textColor.copy(alpha = 0.5f)))
		}
		// Compact callers: hugs its content (as short as possible for the current text),
		// grows as the user types, and the square min-width keeps the empty field visible.
		// It must NOT fill the width: in a Row next to a weighted label it would grab the
		// whole row. Full-width callers fill the caller's width instead, so the whole field
		// area directly takes taps and focus (like CreatorPopup's BorderedTextField).
		BasicTextField(
			state = state,
			lineLimits = TextFieldLineLimits.SingleLine,
			textStyle = style,
			cursorBrush = SolidColor(accent),
			keyboardOptions = KeyboardOptions(
				keyboardType = keyboardType,
				imeAction = imeAction
					?: if (onImeAction != null) ImeAction.Done else ImeAction.Default,
			),
			// Name the action: inside the SAM lambda the implicit `it` is
			// `performDefaultAction`, not the outer `onImeAction` — the naive
			// `let { { it() } }` would just invoke the default focus move.
			onKeyboardAction = onImeAction?.let { action -> { action() } },
			modifier = Modifier
				.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Min))
				.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
		)
	}
}

/** The app's standard body text color (matches `colorPrimaryFg`). */
val DefaultTextColor: Color @Composable get() = colorResource(R.color.colorPrimaryFg)

/** The minimum touch-target height of a full-width-mode [BareTextField]. */
private val MinTouchHeight = 40.dp
