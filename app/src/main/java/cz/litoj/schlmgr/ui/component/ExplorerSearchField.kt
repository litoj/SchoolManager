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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

/**
 * The explorer search field, fully stateless — the text mirrors the
 * screen's collected query, closing it the screen's decision.
 * - threshold, debounce and clearing policy live in the view model.
	 * - both explorers render it against their own state.
	 * - the keyboard’s dismissal closes the field — the system hides the keyboard on the first back press without the app seeing it, so the field leaves with the keyboard instead of waiting for a second press.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExplorerSearchField(
	value: String,
	onValueChange: (String) -> Unit,
	onSubmit: (String) -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val keyboard = LocalSoftwareKeyboardController.current
	val focus = remember { FocusRequester() }
	LaunchedEffect(Unit) { focus.requestFocus() }
	// The submit hides the keyboard on purpose, so its own hiding must not
	// close the field — every other dismissal does.
	var shown by remember { mutableStateOf(false) }
	var ownHide by remember { mutableStateOf(false) }
	val imeVisible = WindowInsets.isImeVisible
	LaunchedEffect(imeVisible) {
		if (imeVisible) {
			shown = true
			ownHide = false
		} else if (shown) {
			shown = false
			if (!ownHide) onClose()
		}
	}
	// Bare on the toolbar and centered like the title, inverted text.
	// Cursor, handles and selection follow the text — the green defaults
	// stay invisible on the green bar.
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
				hint = stringResource(R.string.search),
				onImeAction = {
					onSubmit(value)
					// the results are the point now — drop the keyboard, keep the field
					ownHide = true
					keyboard?.hide()
				},
			)
		}
	}
}
