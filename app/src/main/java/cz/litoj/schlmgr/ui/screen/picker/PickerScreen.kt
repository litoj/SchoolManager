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
import cz.litoj.schlmgr.ui.component.TopBarSearchField
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
 * - the corner holds the select-all button, where an explorer holds its more menu.
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
			if (fieldOpen) TopBarSearchField(query, viewModel::onQueryChange, viewModel::onQuerySubmit, viewModel::closeSearchField, Modifier.weight(1f))
			else TopBarTitle(title)
			TopBarIconButton(
				icon = R.drawable.ic_search,
				visible = searchVisible && !fieldOpen,
				onClick = viewModel::openSearchField,
			)
			TopBarIconButton(
				icon = R.drawable.ic_check_all,
				onClick = viewModel::onSelectAllToggle,
			)
		}
		PickerContent(viewModel, Modifier.weight(1f).headerShadow())
	}
}

/**
 * The explorer content of the picker: the scaffold with the bottom strip of the picker.
 * - the scaffold holds the breadcrumbs, the info strip and the item list.
 * - the strip shows only while rows are selected, like the subjects screen.
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
		callbacks = pickerCallbacks(viewModel, listState.selectionActive),
		modifier = modifier,
	) {
		if (listState.selectionActive) PickerActionBar(
			onCancel = viewModel::cancel,
			onSelect = viewModel::onSelect,
		)
	}
}

/** The row intents, where a selection tap toggles like on the subjects screen. */
private fun pickerCallbacks(viewModel: PickerViewModel, selectionActive: Boolean): ItemListCallbacks = ItemListCallbacks(
	onItemClick = { ui ->
		if (selectionActive) viewModel.setSelected(ui.payload, !ui.selected)
		else viewModel.onItemClick(ui.payload)
	},
	onItemDoubleTap = viewModel::onItemDoubleTap,
	onItemIconClick = { ui -> viewModel.toggleSelect(ui.payload) },
	onItemLongClick = { ui -> viewModel.onItemLongClick(ui.payload) },
	onSelectRange = viewModel::selectRange,
	onSelectEnd = viewModel::endRangeSelect,
	onTopPull = viewModel::openSearchField,
)
