package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cz.litoj.schlmgr.R

/**
 * The top bar search field, fully stateless.
 * - the text mirrors the query of the screen, which decides when to close.
 * - threshold, debounce and clearing policy live in the screen's view model.
 * - a dismissed keyboard closes the field, since the system hides it on the first back press.
 * - so the field leaves with the keyboard instead of waiting for a second press.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopBarSearchField(
	value: String,
	onValueChange: (String) -> Unit,
	onSubmit: (String) -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
	hint: String = stringResource(R.string.search),
) {
	val keyboard = LocalSoftwareKeyboardController.current
	val focus = remember { FocusRequester() }
	LaunchedEffect(Unit) { focus.requestFocus() }
	// the submit hides the keyboard on purpose, so its own hiding must not close
	// the field, while every other dismissal does.
	// - the system also hides the keyboard on pause, and that hiding must not
	//   close the field, since the user only left the app for a moment.
	// - the pause event drives the hide, so the flag is ahead of the inset change.
	var shown by remember { mutableStateOf(false) }
	var ownHide by remember { mutableStateOf(false) }
	var paused by remember { mutableStateOf(false) }
	val imeVisible = WindowInsets.isImeVisible
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE -> paused = true
				Lifecycle.Event.ON_RESUME -> paused = false
				else -> {}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	LaunchedEffect(imeVisible) {
		if (imeVisible) {
			shown = true
			ownHide = false
		} else if (shown) {
			// a pause-driven hide keeps the field, the keyboard reopens on a tap
			if (paused || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
				return@LaunchedEffect
			shown = false
			if (!ownHide) onClose()
		}
	}
	// bare on the toolbar and centered like the title, with inverted text.
	// - the cursor, handles and selection follow the text color.
	// - the green defaults would stay invisible on the green bar.
	val onBar = colorResource(R.color.colorOnHeader)
	val selection = TextSelectionColors(
		handleColor = onBar,
		backgroundColor = onBar.copy(alpha = 0.4f),
	)
	Box(
		modifier.fillMaxHeight(),
		contentAlignment = Alignment.CenterStart,
	) {
		CompositionLocalProvider(LocalTextSelectionColors provides selection) {
			WideTextField(
				value = value,
				onValueChange = onValueChange,
				fontSize = 17.sp,
				style = BareTextFieldStyle(
					textColor = onBar,
					cursorColor = onBar,
					hintColor = colorResource(R.color.colorSearchHint),
					underline = false,
				),
				focusRequester = focus,
				imeAction = ImeAction.Search,
				hint = hint,
				onImeAction = {
					onSubmit(value)
					// the results are the point now, so drop the keyboard but keep the field
					ownHide = true
					keyboard?.hide()
				},
			)
		}
	}
}
