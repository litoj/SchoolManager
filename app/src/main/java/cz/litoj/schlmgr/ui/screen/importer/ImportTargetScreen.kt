package cz.litoj.schlmgr.ui.screen.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ActionStripHeight
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ImportActionBar
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.ListEndReveal
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel

/**
 * The import mover screen: the explorer picks the target, the always-visible bar acts.
 * - the file URI stays with the view model, so the activity only starts and finishes it.
 * - the result reloads the target list and turns the bar into Done.
 */
@Composable
fun ImportTargetScreen(
	viewModel: ImportTargetViewModel,
	onExit: () -> Unit,
	onDone: (List<Int>) -> Unit,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	val target by viewModel.targetState.collectAsStateWithLifecycle()
	val snackbar = messageSnackbarHost()
	val scrollState = rememberLazyListState()
	ListEndReveal(listState, scrollState)
	// a busy import keeps the target, so back and cancel cannot change or lose it.
	val leave = {
		if (!target.busy) {
			when (viewModel.onBackRequested()) {
				ExplorerViewModel.BackOutcome.HANDLED -> {}
				else -> onExit()
			}
		}
	}
	BackHandler { leave() }

	Box(Modifier.fillMaxSize().navigationBarsPadding()) {
		Column(Modifier.fillMaxSize()) {
			TopBar {
				TopBarNav(up = true, onNavigate = leave)
				TopBarTitle(stringResource(R.string.import_file_title, target.fileName))
			}
			ExplorerScaffold(
				state = state,
				listState = listState,
				onBreadcrumbClick = { if (!target.busy) viewModel.breadcrumbClick(it) },
				onInfoClick = viewModel::toggleInfoExpanded,
				callbacks = importCallbacks(viewModel, target.busy),
				scrollState = scrollState,
				modifier = Modifier.weight(1f).headerShadow(),
			) {
				ImportActionBar(
					busy = target.busy,
					imported = target.imported,
					onCancel = onExit,
					onImport = viewModel::importNow,
					onDone = { onDone(viewModel.targetPathIds) },
				)
			}
		}
		// the snackbar sits above the action strip.
		SnackbarHost(
			hostState = snackbar,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = ActionStripHeight),
		)
	}
}

/** The mover rows only open containers, and a description still opens its popup. */
private fun importCallbacks(viewModel: ImportTargetViewModel, busy: Boolean): ItemListCallbacks =
	ItemListCallbacks(
		onItemClick = { ui -> if (!busy) viewModel.onItemClick(ui.payload) },
		onInfo = { ui -> MessagePopup(ui.description) },
		onInnerInfo = { _, line -> MessagePopup(line.description) },
	)
