package cz.litoj.schlmgr.ui.screen.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ExplorerSearchField
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PickerActionBar
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.viewmodel.SearchUi

/**
 * The source picker screen — top bar with the up arrow (a pushed
 * destination, not a drawer sibling) and the search field in the title
 * slot, the explorer content below, the back chain over it. The exit
 * pops back to the test setup.
 *
 * @param onExit the up arrow / the exit back outcome — pops this destination
 */
@Composable
fun PickerScreen(viewModel: PickerViewModel, onExit: () -> Unit) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	// the toolbar title is derived — the open container's crumb, the picker label at the root
	val title = state.breadcrumbs.lastOrNull() ?: stringResource(R.string.test_add)
	val query by viewModel.query.collectAsStateWithLifecycle()
	// chrome derives from the one search mode (see [SearchUi])
	val fieldOpen = state.searchUi == SearchUi.TYPING
	val searchVisible = state.breadcrumbs.isNotEmpty()

	// the picker's one navigation step — confirm or cancel closes it
	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			if (request is PickerViewModel.Request.Close) onExit()
		}
	}

	ExplorerBackHandler(viewModel, onExit)

	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(up = true, onNavigate = onExit)
			if (fieldOpen) ExplorerSearchField(query, viewModel::onQueryChange, viewModel::onQuerySubmit, Modifier.weight(1f))
			else TopBarTitle(title)
			TopBarIconButton(
				icon = R.drawable.ic_search,
				visible = searchVisible && !fieldOpen,
				onClick = viewModel::openSearchField,
			)
		}
		PickerContent(viewModel, Modifier.weight(1f).headerShadow())
	}
}

/**
 * The picker's explorer content — the scaffold (breadcrumbs, info strip,
 * item list) with the picker's bottom strip. The Select button's
 * enablement derives from the list state, so it recomposes as rows get
 * (un)selected.
 */
@Composable
private fun PickerContent(viewModel: PickerViewModel, modifier: Modifier = Modifier) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	ExplorerScaffold(
		state = state,
		listState = listState,
		onBreadcrumbClick = viewModel::breadcrumbClick,
		onInfoClick = viewModel::toggleInfoExpanded,
		callbacks = pickerCallbacks(viewModel),
		modifier = modifier,
	) {
		PickerActionBar(
			selectEnabled = listState.anySelected,
			onCancel = viewModel::cancel,
			onSelectAll = viewModel::onSelectAllToggle,
			onSelect = viewModel::onSelect,
		)
	}
}

/**
 * The picker's row intents — a browse row opens its container (a word
 * flips), a long-press toggles the selection (a search hit included),
 * the drag-select covers ranges.
 */
private fun pickerCallbacks(viewModel: PickerViewModel): ItemListCallbacks = ItemListCallbacks(
	onItemClick = { ui -> viewModel.onItemClick(ui.payload) },
	onItemLongClick = { ui -> viewModel.onItemLongClick(ui.payload) },
	onSelectRange = viewModel::selectRange,
	onSelectEnd = viewModel::endRangeSelect,
)
