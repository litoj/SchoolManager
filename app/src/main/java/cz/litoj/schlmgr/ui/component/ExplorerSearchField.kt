package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel

/**
 * The one explorer search field, driven by an [ExplorerViewModel]: the text
 * mirrors [ExplorerViewModel.query], keystrokes go straight to
 * [ExplorerViewModel.onQueryChange], and the keyboard search key submits through
 * [ExplorerViewModel.onQuerySubmit]. The field stays open after a submit (the
 * query stays editable); closing it is a separate decision of the view model.
 * Threshold, debounce and clearing decisions live in the view model — this view
 * holds no policy.
 *
 * Any host (the shell toolbar, the picker) renders this same field against its
 * own view model instance.
 */
@Composable
fun ExplorerSearchField(
  viewModel: ExplorerViewModel,
  modifier: Modifier = Modifier,
) {
  val query by viewModel.query.collectAsStateWithLifecycle()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  // Bare on the toolbar and centered like the title, inverted text.
  // Cursor, handles and selection follow the text: the green defaults
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
      BareTextField(
        value = query,
        onValueChange = viewModel::onQueryChange,
        fontSize = 17.sp,
        fillWidth = true,
        focusRequester = focus,
        imeAction = ImeAction.Search,
        hint = stringResource(R.string.search),
        textColor = onBar,
        cursorColor = onBar,
        hintColor = colorResource(R.color.colorSearchHint),
        underline = false,
        onImeAction = {
          viewModel.onQuerySubmit(query)
          // the results are the point now: drop the keyboard, keep the field
          keyboard?.hide()
        },
      )
    }
  }
}
