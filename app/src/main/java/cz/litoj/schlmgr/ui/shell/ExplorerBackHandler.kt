package cz.litoj.schlmgr.ui.shell

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
fun ExplorerBackHandler(viewModel: ExplorerViewModel<*>, onExitApp: () -> Unit) {
	val context = LocalContext.current
	BackHandler {
		when (viewModel.onBackRequested()) {
			ExplorerViewModel.BackOutcome.HANDLED -> {}
			ExplorerViewModel.BackOutcome.EXIT_HINT ->
				Toast.makeText(context, R.string.press_exit, Toast.LENGTH_SHORT).show()
			ExplorerViewModel.BackOutcome.EXIT -> onExitApp()
		}
	}
}
