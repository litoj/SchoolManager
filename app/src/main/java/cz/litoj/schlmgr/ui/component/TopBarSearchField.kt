package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cz.litoj.schlmgr.R

/**
 * The top bar search field, fully stateless.
 * - the text mirrors the query of the screen, which decides what a dismissal means.
 * - an empty query closes the field, a written one keeps it with its results.
 * - a pause-driven hide reports nothing, since the user only left the app for a moment.
 *
 * @param onHelp shows the help icon at the end of the field, null leaves it out
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopBarSearchField(
	value: String,
	onValueChange: (String) -> Unit,
	onSubmit: (String) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	hint: String = stringResource(R.string.search),
	onHelp: (() -> Unit)? = null,
) {
	val keyboard = LocalSoftwareKeyboardController.current
	val focus = remember { FocusRequester() }
	val focusManager = LocalFocusManager.current
	LaunchedEffect(Unit) { focus.requestFocus() }
	// the help hides the keyboard on purpose, so that hiding is no dismissal.
	// - the system also hides the keyboard on pause, and that hiding must not
	//   count as a dismissal, since the user only left the app for a moment.
	// - the pause event drives the hide, so the flag is ahead of the inset change.
	var shown by remember { mutableStateOf(false) }
	var ownHide by remember { mutableStateOf(false) }
	var paused by remember { mutableStateOf(false) }
	fun hideKeyboard() {
		ownHide = true
		keyboard?.hide()
	}
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
			if (ownHide) return@LaunchedEffect
			// the focus leaves with the keyboard, so the cursor stops blinking
			// - a tap on the field returns both
			focusManager.clearFocus()
			// the query decides on the screen: an empty one closes, a written one keeps the field
			onDismiss()
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
	Row(
		modifier.fillMaxHeight(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		CompositionLocalProvider(LocalTextSelectionColors provides selection) {
			WideTextField(
				value = value,
				onValueChange = onValueChange,
				fontSize = 17.sp,
				modifier = Modifier.weight(1f),
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
					keyboard?.hide()
				},
			)
		}
		if (onHelp != null) Box(
			Modifier
				.width(50.dp)
				.fillMaxHeight()
				.clickable {
					// the popup window hides the keyboard anyway, so the hide goes first as our own
					hideKeyboard()
					onHelp()
				},
			contentAlignment = Alignment.Center,
		) {
			Icon(
				painterResource(R.drawable.ic_help),
				contentDescription = stringResource(R.string.help),
				tint = onBar,
				modifier = Modifier.size(IconSize),
			)
		}
	}
}
