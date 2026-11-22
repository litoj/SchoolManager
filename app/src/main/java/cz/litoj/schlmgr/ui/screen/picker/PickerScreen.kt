package cz.litoj.schlmgr.ui.screen.picker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
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
import cz.litoj.schlmgr.ui.component.ExplorerSearchControls
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PickerActionBar
import cz.litoj.schlmgr.ui.shell.editFilter
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.explorerBack
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.shell.pickExplorerMenu
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.explorerMenu
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.SearchUi

/**
 * The source picker screen: a pushed destination, not a drawer sibling.
 * - the top bar holds the up arrow and the search field in the title slot.
 * - the header menu carries select all and the filter, as an explorer header does.
 * - the explorer content sits below, with the back chain over it.
 * - the exit pops back to the test setup.
 *
 * @param title the purpose of this pick, such as a source or a reference
 * @param onExit the exit back outcome and the closing request, which pop this destination
 */
@Composable
fun PickerScreen(
	viewModel: PickerViewModel,
	onExit: () -> Unit,
	@StringRes title: Int = R.string.test_add,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val query by viewModel.query.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	// chrome derives from the one search mode, see [SearchUi]
	// the field stays through the results, since the query marks what the list shows
	val fieldOpen = state.searchUi != SearchUi.OFF
	val searchBlockedReason = viewModel.searchBlockedReason

	// the one navigation step of the picker, where confirm or cancel closes it
	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			if (request is PickerViewModel.Request.Close) onExit()
		}
	}

	ExplorerBackHandler(viewModel, onExit)
	val context = LocalContext.current

	BoxWithConstraints(Modifier.fillMaxSize()) {
		Column(Modifier.fillMaxSize()) {
		TopBar {
			// the arrow ends a search, then walks up the folder chain, and the picks stay
			TopBarNav(
				up = true,
				onNavigate = { explorerBack(viewModel, onExit, context) },
				contentDescription = stringResource(R.string.back),
			)
		ExplorerSearchControls(
			title = stringResource(title),
			query = query,
			fieldOpen = fieldOpen,
			searchBlockedReason = searchBlockedReason,
			entries = explorerMenu(
				viewModel.menuKind, selecting = false, singleSelected = false,
				ActionConfig.load(),
				filterActive = (state.info as? InfoBar.Search)?.criteria?.isNotEmpty() == true,
				searchBlockedReason = searchBlockedReason,
			),
			width = this@BoxWithConstraints.maxWidth,
			onOpenSearch = viewModel::openSearchField,
			onQueryChange = viewModel::onQueryChange,
			onQuerySubmit = viewModel::onQuerySubmit,
			onDismissSearch = viewModel::dismissSearch,
			onMenuPick = { pickExplorerMenu(viewModel, it) },
		)
		}
		PickerContent(viewModel, Modifier.weight(1f).headerShadow())
		}
	}
}

/**
 * The explorer content of the picker: the scaffold with the bottom strip of the picker.
 * - the scaffold holds the breadcrumbs, the info strip and the item list.
 * - the strip shows only while rows are picked, in this folder or any other.
 */
@Composable
private fun PickerContent(viewModel: PickerViewModel, modifier: Modifier = Modifier) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	val picked = viewModel.batchRows().size
	ExplorerScaffold(
		state = state,
		listState = listState,
		onBreadcrumbClick = viewModel::breadcrumbClick,
		onInfoClick = viewModel::toggleInfoExpanded,
		onFilterEdit = { editFilter(viewModel, it) },
		onFilterDrop = viewModel::dropFilter,
		onParentClick = { viewModel.onBackRequested() },
		callbacks = pickerCallbacks(viewModel),
		onScrollPosition = viewModel::onScrollPosition,
		modifier = modifier,
		statusLine = if (picked > 0) stringResource(R.string.data_selected, picked) else null,
	) {
		if (picked > 0) PickerActionBar(
			onCancel = viewModel::cancel,
			onSelect = viewModel::onSelect,
		)
	}
}

/** The row intents, where a tap keeps its meaning so the picks can span folders. */
private fun pickerCallbacks(viewModel: PickerViewModel): ItemListCallbacks = ItemListCallbacks(
	onItemClick = { index -> viewModel.onItemClick(viewModel.rowAt(index)) },
	onItemDoubleTap = viewModel::onItemDoubleTap,
	onItemIconClick = { index -> viewModel.toggleSelect(viewModel.rowAt(index)) },
	onItemLongClick = { index -> viewModel.onItemLongClick(viewModel.rowAt(index)) },
	onSelectRange = viewModel::selectRange,
	onSelectEnd = viewModel::endRangeSelect,
	onTopPull = viewModel::openSearchField,
)
