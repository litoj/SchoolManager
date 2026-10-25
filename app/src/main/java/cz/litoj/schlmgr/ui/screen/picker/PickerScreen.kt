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
 * The source picker screen: a pushed destination, not a drawer sibling.
 * - the top bar holds the up arrow and the search field in the title slot.
 * - the explorer content sits below, with the back chain over it.
 * - the exit pops back to the test setup.
 *
 * @param onExit the up arrow and the exit back outcome, which pops this destination
 */
@Composable
fun PickerScreen(viewModel: PickerViewModel, onExit: () -> Unit) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	// the toolbar title is derived: the crumb of the open container, the picker label at the root
	val title = state.breadcrumbs.lastOrNull() ?: stringResource(R.string.test_add)
	val query by viewModel.query.collectAsStateWithLifecycle()
	// chrome derives from the one search mode, see [SearchUi]
	val fieldOpen = state.searchUi == SearchUi.TYPING
	val searchVisible = state.breadcrumbs.isNotEmpty()

	// the one navigation step of the picker, where confirm or cancel closes it
	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			if (request is PickerViewModel.Request.Close) onExit()
		}
	}

	ExplorerBackHandler(viewModel, onExit)

	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(up = true, onNavigate = onExit)
			if (fieldOpen) ExplorerSearchField(query, viewModel::onQueryChange, viewModel::onQuerySubmit, viewModel::closeSearchField, Modifier.weight(1f))
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
 * The explorer content of the picker: the scaffold with the bottom strip of the picker.
 * - the scaffold holds the breadcrumbs, the info strip and the item list.
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

private fun pickerCallbacks(viewModel: PickerViewModel): ItemListCallbacks = ItemListCallbacks(
	onItemClick = { ui -> viewModel.onItemClick(ui.payload) },
	onItemDoubleTap = viewModel::onItemDoubleTap,
	onItemIconClick = { ui -> viewModel.toggleSelect(ui.payload) },
	onItemLongClick = { ui -> viewModel.onItemLongClick(ui.payload) },
	onSelectRange = viewModel::selectRange,
	onSelectEnd = viewModel::endRangeSelect,
	onTopPull = viewModel::openSearchField,
)
