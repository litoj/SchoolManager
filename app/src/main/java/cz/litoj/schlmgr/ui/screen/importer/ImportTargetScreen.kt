package cz.litoj.schlmgr.ui.screen.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ActionStripHeight
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ExplorerSearchControls
import cz.litoj.schlmgr.ui.component.ImportActionBar
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.popup.InfoPopup
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.editFilter
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.explorerBack
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.shell.MessageSnackbarHost
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.shell.pickExplorerMenu
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.explorerMenu
import cz.litoj.schlmgr.ui.viewmodel.SearchUi

/**
 * The import mover screen: the explorer picks the target, the always-visible bar acts.
 * - the file URI stays with the view model, so the activity only starts and finishes it.
 * - the result reloads the target list and turns the bar into Done.
 * - it carries the explorer search, sort and filter, while its rows stay plain.
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
	val query by viewModel.query.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val snackbar = messageSnackbarHost()
	val scrollState = rememberLazyListState()
	val fieldOpen = state.searchUi != SearchUi.OFF
	val searchBlockedReason = viewModel.searchBlockedReason
	// a busy import keeps the target, so back and cancel cannot change or lose it.
	// the mover rides the explorer back chain, where one press leaves the mover
	val leave = {
		if (!target.busy) explorerBack(viewModel, onExit, context)
	}
	BackHandler(target.busy) {}
	ExplorerBackHandler(viewModel, onExitApp = onExit, enabled = !target.busy)

	BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
		Column(Modifier.fillMaxSize()) {
			TopBar {
				TopBarNav(
					up = true,
					onNavigate = leave,
					enabled = !target.busy,
					blockedReason = R.string.unavailable_import_busy,
					contentDescription = stringResource(R.string.back),
				)
				ExplorerSearchControls(
					title = stringResource(R.string.import_file_title, target.fileName),
					query = query,
					fieldOpen = fieldOpen,
					searchBlockedReason = searchBlockedReason,
					entries = explorerMenu(
						viewModel.menuKind, selecting = false, singleSelected = false,
						ActionConfig.load(),
						filterActive = (state.info as? InfoBar.Search)?.criteria?.isNotEmpty() == true,
						searchBlockedReason = searchBlockedReason,
						// the mover marks no rows, so it offers no select all
						selectAll = false,
					),
					width = this@BoxWithConstraints.maxWidth,
					onOpenSearch = viewModel::openSearchField,
					onQueryChange = viewModel::onQueryChange,
					onQuerySubmit = viewModel::onQuerySubmit,
					onDismissSearch = viewModel::dismissSearch,
					onMenuPick = { pickExplorerMenu(viewModel, it) },
				)
				TopBarIconButton(
					icon = R.drawable.ic_help,
					contentDescription = stringResource(R.string.help),
					onClick = {
						InfoPopup(R.string.help_import_format, R.string.help_import_format_body)
					},
				)
			}
			ExplorerScaffold(
				state = state,
				listState = listState,
				onBreadcrumbClick = { if (!target.busy) viewModel.breadcrumbClick(it) },
				onInfoClick = viewModel::toggleInfoExpanded,
				onFilterEdit = { editFilter(viewModel, it) },
				onFilterDrop = viewModel::dropFilter,
				onParentClick = { if (!target.busy) viewModel.onBackRequested() },
				callbacks = importCallbacks(viewModel, target.busy),
				onSibling = { if (!target.busy) viewModel.openSibling(it) },
				scrollState = scrollState,
				onScrollPosition = viewModel::onScrollPosition,
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
		MessageSnackbarHost(
			hostState = snackbar,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = ActionStripHeight),
		)
	}
}

/** The mover rows only open containers, and a swipe down opens the search. */
private fun importCallbacks(viewModel: ImportTargetViewModel, busy: Boolean): ItemListCallbacks =
	ItemListCallbacks(
		onItemClick = { index -> if (!busy) viewModel.onItemClick(viewModel.rowAt(index)) },
		onTopPull = { if (!busy) viewModel.openSearchField() },
	)
