package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.litoj.schlmgr.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PasteActionBar
import cz.litoj.schlmgr.ui.component.SelectionActionBar
import cz.litoj.schlmgr.ui.component.fabDragVisibility
import cz.litoj.schlmgr.ui.component.rememberFabVisibility
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.shell.editFilter
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.selectionOverflowMenu

/**
 * The explorer content of the subjects screen.
 * - the scaffold holds the action strip, the sibling swipe and the floating New button.
 */
@Composable
internal fun SubjectsContent(
	viewModel: MainExplorerViewModel,
	exportBlockedReason: Int,
	modifier: Modifier = Modifier,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	val searching = listState.variant == ExplorerRowVariant.Search
	val scrollState = rememberLazyListState()
	// a held long-press keeps the strip away, so the list never shrinks under the drag
	var pressHeld by remember { mutableStateOf(false) }
	// the New button visibility follows the drag gesture, not the list scroll
	val fabVisibility = rememberFabVisibility()
	// a load landing resets the button, an insert or delete keeps it hidden
	LaunchedEffect(state.contentStep) { fabVisibility.show() }
	Box(modifier) {
		val callbacks = mainCallbacks(viewModel, listState.selectionActive) { pressHeld = it }
		val armedStatus = if (viewModel.pasteArmed) stringResource(
			if (viewModel.armedReferences) R.string.paste_status_references else R.string.paste_status_move,
			viewModel.armedCount,
		) else null
		val selectionStatus = if (listState.selectionActive)
			stringResource(R.string.data_selected, listState.selectionCount) else null
		ExplorerScaffold(
			state = state,
			listState = listState,
			onBreadcrumbClick = viewModel::breadcrumbClick,
			onInfoClick = viewModel::toggleInfoExpanded,
			onFilterEdit = { editFilter(viewModel, it) },
			onFilterDrop = viewModel::dropFilter,
			onParentClick = { viewModel.onBackRequested() },
			callbacks = callbacks,
			onSibling = viewModel::openSibling,
			scrollState = scrollState,
			onScrollPosition = viewModel::onScrollPosition,
			// the armed status takes the top line, so the bottom strip holds only its actions
			statusLine = armedStatus ?: selectionStatus,
			modifier = Modifier.fillMaxSize()
				.fabDragVisibility(fabVisibility) { scrollState.canScrollForward || scrollState.canScrollBackward },
		) {
			when {
				viewModel.pasteArmed -> PasteActionBar(
					onCancel = viewModel::cancelPaste,
					onPaste = viewModel::doPaste,
					blockedReason = viewModel.pasteBlockedReason,
				)
				// the row shows its selection tint at once, the strip only after the lift
				listState.selectionActive && !pressHeld -> {
					// the read lands on every recomposition, so a settings change reaches the next selection
					val config = ActionConfig.load()
					SelectionActionBar(
						config = config,
						search = searching,
						singleSelected = listState.singleSelected,
						onAction = viewModel::onSelectionAction,
						overflowEntries = { direct ->
							selectionOverflowMenu(
								config, searching, listState.singleSelected, exportBlockedReason, direct,
							)
						},
						onOverflow = viewModel::onSelectionMenuPick,
						exportBlockedReason = exportBlockedReason,
					)
				}
				else -> {}
			}
		}
		SubjectsFab(
			viewModel, listState, fabVisibility, Modifier.matchParentSize())
	}
}

/** The row intents, where hits carry their track path and a selection tap toggles. */
private fun mainCallbacks(
	viewModel: MainExplorerViewModel,
	selectionActive: Boolean,
	onPressHeld: (Boolean) -> Unit,
): ItemListCallbacks =
	ItemListCallbacks(
		onItemClick = { index ->
			viewModel.rowAt(index)?.let { row ->
				if (selectionActive) viewModel.setSelected(row, !row.selected)
				else viewModel.onItemClick(row)
			}
		},
		onItemDoubleTap = viewModel::onItemDoubleTap,
		onItemIconClick = { index -> viewModel.toggleSelect(viewModel.rowAt(index)) },
		onItemLongClick = { index -> viewModel.onItemLongClick(viewModel.rowAt(index)) },
		onInfo = { index -> viewModel.rowAt(index)?.let { MessagePopup(it.description) } },
		onReference = viewModel::onReferenceClick,
		onInnerInfo = { _, line -> MessagePopup(line.description) },
		onSelectRange = viewModel::selectRange,
		onSelectRangeTo = viewModel::selectRangeTo,
		onSelectEnd = viewModel::endRangeSelect,
		onReorderMove = viewModel::moveRow,
		onReorderDrop = viewModel::persistOrder,
		onTopPull = viewModel::openSearchField,
		onTopPullReversed = viewModel::closeSearchField,
		onPressHeld = onPressHeld,
	)
