package cz.litoj.schlmgr.ui.screen.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.TopBarSearchField
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PickerActionBar
import cz.litoj.schlmgr.ui.popup.SearchFilterPopup
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.explorerBack
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.viewmodel.SearchUi

/**
 * The source picker screen: a pushed destination, not a drawer sibling.
 * - the top bar holds the up arrow and the search field in the title slot.
 * - the corner holds the select-all button, where an explorer holds its more menu.
 * - the explorer content sits below, with the back chain over it.
 * - the exit pops back to the test setup.
 *
 * @param onExit the exit back outcome and the closing request, which pop this destination
 */
@Composable
fun PickerScreen(viewModel: PickerViewModel, onExit: () -> Unit) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val query by viewModel.query.collectAsStateWithLifecycle()
	// chrome derives from the one search mode, see [SearchUi]
	// the field stays through the results, since the query marks what the list shows
	val fieldOpen = state.searchUi != SearchUi.OFF
	val searchVisible = state.breadcrumbs.isNotEmpty()

	// the one navigation step of the picker, where confirm or cancel closes it
	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			if (request is PickerViewModel.Request.Close) onExit()
		}
	}

	ExplorerBackHandler(viewModel, onExit)
	val context = LocalContext.current

	Column(Modifier.fillMaxSize()) {
		TopBar {
			// the arrow walks the back chain, so a search or a selection ends before the picker
			TopBarNav(
				up = true,
				onNavigate = { explorerBack(viewModel, onExit, context) },
				contentDescription = stringResource(R.string.back),
			)
			if (fieldOpen) TopBarSearchField(
				query, viewModel::onQueryChange, viewModel::onQuerySubmit, viewModel::dismissSearch,
				Modifier.weight(1f),
				onFilter = {
					SearchFilterPopup(query) { filter ->
						viewModel.onQuerySubmit(filter?.toQuery() ?: "")
					}
				},
			)
			else TopBarTitle(stringResource(R.string.test_add))
			TopBarIconButton(
				icon = R.drawable.ic_search,
				contentDescription = stringResource(R.string.search),
				visible = !fieldOpen,
				enabled = searchVisible,
				blockedReason = if (searchVisible) 0 else R.string.unavailable_search_root,
				onClick = viewModel::openSearchField,
			)
			TopBarIconButton(
				icon = R.drawable.ic_check_all,
				contentDescription = stringResource(R.string.select_all),
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
		statusLine = if (listState.selectionActive)
			stringResource(R.string.data_selected, listState.selectionCount)
		else stringResource(R.string.picker_hint),
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
