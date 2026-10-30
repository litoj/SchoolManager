package cz.litoj.schlmgr.ui.shell

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel

/**
 * The explorer back chain: the view model decides.
 * - the view shows the exit hint and exits.
 */
@Composable
fun ExplorerBackHandler(
	viewModel: ExplorerViewModel<*>,
	onExitApp: () -> Unit,
	/** False keeps the back key for the layer above, such as a busy import. */
	enabled: Boolean = true,
) {
	val context = LocalContext.current
	BackHandler(enabled = enabled) { explorerBack(viewModel, onExitApp, context) }
}

/** The outcome handling of one back press, shared by the back key and the up arrow. */
fun explorerBack(viewModel: ExplorerViewModel<*>, onExitApp: () -> Unit, context: Context) {
	when (viewModel.onBackRequested()) {
		ExplorerViewModel.BackOutcome.HANDLED -> {}
		ExplorerViewModel.BackOutcome.EXIT_HINT ->
			Toast.makeText(context, R.string.press_exit, Toast.LENGTH_SHORT).show()
		ExplorerViewModel.BackOutcome.EXIT -> onExitApp()
	}
}
