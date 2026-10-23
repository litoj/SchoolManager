package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
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
 * Compact field with bare text, accent cursor and a thin bottom line.
 * Never narrower than tall, even empty.
 */
@Composable
fun BareTextField(
	value: String,
	onValueChange: (String) -> Unit,
	fontSize: TextUnit,
	modifier: Modifier = Modifier,
	keyboardType: KeyboardType = KeyboardType.Text,
	style: BareTextFieldStyle = BareTextFieldStyle(),
	onFocusLost: (() -> Unit)? = null,
) = BareTextFieldCore(
	value = value,
	onValueChange = onValueChange,
	fontSize = fontSize,
	modifier = modifier,
	keyboardType = keyboardType,
	style = style,
	onFocusLost = onFocusLost,
)

/**
 * Full-row form field: taps and focus land anywhere, a hint covers an empty
 * value, and the field carries the IME-action and focus chain between fields.
 */
@Composable
fun WideTextField(
	value: String,
	onValueChange: (String) -> Unit,
	fontSize: TextUnit,
	modifier: Modifier = Modifier,
	hint: String? = null,
	style: BareTextFieldStyle = BareTextFieldStyle(),
	imeAction: ImeAction? = null,
	onImeAction: (() -> Unit)? = null,
	focusRequester: FocusRequester? = null,
	singleLine: Boolean = true,
) = BareTextFieldCore(
	value = value,
	onValueChange = onValueChange,
	fontSize = fontSize,
	modifier = modifier,
	hint = hint,
	style = style,
	fillWidth = true,
	imeAction = imeAction,
	onImeAction = onImeAction,
	focusRequester = focusRequester,
	singleLine = singleLine,
)

/**
 * The shared body behind the two field shapes. [fillWidth] stays an
 * implementation detail, not a mode flag of the caller.
 */
@Composable
private fun BareTextFieldCore(
	value: String,
	onValueChange: (String) -> Unit,
	fontSize: TextUnit,
	modifier: Modifier = Modifier,
	keyboardType: KeyboardType = KeyboardType.Text,
	hint: String? = null,
	style: BareTextFieldStyle = BareTextFieldStyle(),
	fillWidth: Boolean = false,
	focusRequester: FocusRequester? = null,
	imeAction: ImeAction? = null,
	onFocusLost: (() -> Unit)? = null,
	onImeAction: (() -> Unit)? = null,
	singleLine: Boolean = true,
) {
	// theme colors captured here, because the draw scope below cannot read them
	val accent = colorResource(R.color.colorPrimaryHeader)
	val idleBar = colorResource(R.color.colorControlLine)
	// unspecified means the standard look, since the resource reads cannot be
	// data-class defaults, so they resolve here
	val textColor = if (style.textColor != Color.Unspecified) style.textColor else DefaultTextColor
	val cursorColor = if (style.cursorColor != Color.Unspecified) style.cursorColor else accent
	val hintColor = style.hintColor.takeIf { it != Color.Unspecified } ?: textColor.copy(alpha = 0.5f)
	var focused by remember { mutableStateOf(false) }
	val state = rememberTextFieldState(value)
	if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
	// the value of the parent: an emission equal to it is the echo of the
	// programmatic sync above, not user input
	val echoed by rememberUpdatedState(value)
	LaunchedEffect(state) {
		// skipping the first emission avoids echoing the seeded value
		snapshotFlow { state.text.toString() }
			.drop(1)
			.collect { if (it != echoed) onValueChange(it) }
	}
	val textStyle = TextStyle(fontSize = fontSize, color = textColor)
	Box(
		modifier
			// thin bottom line, gray when idle and green while focused.
			// - sits before the square-clamp layout below.
			// - later draw modifiers paint in the inner pre-clamp space.
			// - this order makes the line span the full clamped width.
			.drawBehind {
				if (!style.underline) return@drawBehind
				val y = size.height - 1.dp.toPx()
				drawLine(
					color = if (focused) accent else idleBar,
					start = Offset(0f, y),
					end = Offset(size.width, y),
					strokeWidth = 1.dp.toPx(),
				)
			}
			// compact default: a one-line field never renders narrower than tall.
			// - the minimum width clamps to the rendered height in the same pass.
			// - full-width callers size to the text height.
			.then(
				if (fillWidth) Modifier
				else Modifier.layout { measurable, constraints ->
					val placeable = measurable.measure(constraints)
					val minHeight = placeable.height
					val minWidth = maxOf(placeable.width, minHeight)
					layout(minWidth, minHeight) { placeable.place(0, 0) }
				},
			)
			.onFocusChanged {
				// hasFocus, not isFocused: only the inner field holds the focus.
				// - the wrapper around it keeps isFocused false.
				focused = it.hasFocus
				if (!it.hasFocus) onFocusLost?.invoke()
			},
		contentAlignment = Alignment.CenterStart,
	) {
		if (state.text.isEmpty() && hint != null) {
			Text(hint, style = textStyle.copy(color = hintColor))
		}
		// compact callers get a field that hugs its content and grows as the user types.
		// - the square min-width keeps the empty field visible.
		// - it must not fill the width, or it grabs the whole row next to a weighted label.
		// - full-width callers fill the width instead, so the area takes taps and focus.
		BasicTextField(
			state = state,
			lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
			textStyle = textStyle,
			cursorBrush = SolidColor(cursorColor),
			keyboardOptions = KeyboardOptions(
				keyboardType = keyboardType,
				imeAction = imeAction
					?: if (onImeAction != null) ImeAction.Done else ImeAction.Default,
			),
			// name the action: inside the SAM lambda the implicit it is
			// performDefaultAction, not the outer onImeAction.
			// - a plain let with an inner it would invoke the default focus move.
			onKeyboardAction = onImeAction?.let { action -> { action() } },
			modifier = Modifier
				.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Min))
				.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
		)
	}
}

val DefaultTextColor: Color @Composable get() = colorResource(R.color.colorPrimaryFg)

/**
 * The appearance overrides of [BareTextField] and [WideTextField].
 * - the color escapes for inverted surfaces and the chrome-free form.
 * - one value, so the signature of the field carries behavior only.
 * - an unspecified color means the standard look, resolved inside.
 */
data class BareTextFieldStyle(
	val textColor: Color = Color.Unspecified,
	val cursorColor: Color = Color.Unspecified,
	val hintColor: Color = Color.Unspecified,
	val underline: Boolean = true,
)

