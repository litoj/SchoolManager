package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PasteActionBar
import cz.litoj.schlmgr.ui.component.SelectionActionBar
import cz.litoj.schlmgr.ui.component.fabDragVisibility
import cz.litoj.schlmgr.ui.component.ListEndReveal
import cz.litoj.schlmgr.ui.component.rememberFabVisibility
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant

/**
 * The explorer content of the subjects screen.
 * - the scaffold holds the action strip and the floating New button.
 * - the sibling swipe sits over all of it.
 */
@Composable
internal fun SubjectsContent(
	viewModel: MainExplorerViewModel,
	exportBlockedReason: Int,
	modifier: Modifier = Modifier,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	// a horizontal swipe switches to the sibling.
	// the pixel threshold keys the gesture, so a density change restarts it
	val swipeThreshold = with(LocalDensity.current) { 40.dp.toPx() }
	val searching = listState.variant == ExplorerRowVariant.Search
	var swipe by remember { mutableFloatStateOf(0f) }
	val openSibling by rememberUpdatedState(viewModel::openSibling)
	val scrollState = rememberLazyListState()
	// a held long-press keeps the strip away, so the list never shrinks under the drag
	var pressHeld by remember { mutableStateOf(false) }
	// the New button visibility follows the drag gesture, not the list scroll
	val fabVisibility = rememberFabVisibility()
	ListEndReveal(listState, scrollState)
	// the anchor lands before the slide composes the incoming pane, so it shows in place
	LaunchedEffect(listState.anchor) {
		listState.anchor?.let { scrollState.scrollToItem(it.index, it.offset) }
	}
	// the list keeps the one live scroll, which every history snapshot captures on leaving
	LaunchedEffect(scrollState) {
		snapshotFlow {
			scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
		}.collect { (index, offset) -> viewModel.onScrollPosition(index, offset) }
	}
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
			callbacks = callbacks,
			scrollState = scrollState,
			listArea = { SiblingSlideList(state, listState, callbacks, scrollState, Modifier.weight(1f)) },
			// the armed status takes the top line, so the bottom strip holds only its actions
			statusLine = armedStatus ?: selectionStatus,
			modifier = Modifier.fillMaxSize()
				.fabDragVisibility(fabVisibility) { scrollState.canScrollForward || scrollState.canScrollBackward }
				.pointerInput(swipeThreshold) {
				detectHorizontalDragGestures(
					onHorizontalDrag = { _, amount -> swipe += amount },
					onDragEnd = {
						if (swipe < -swipeThreshold) openSibling(1)
						else if (swipe > swipeThreshold) openSibling(-1)
						swipe = 0f
					},
					onDragCancel = { swipe = 0f },
				)
			},
		) {
			when {
				viewModel.pasteArmed -> PasteActionBar(
					onCancel = viewModel::cancelPaste,
					onPaste = viewModel::doPaste,
				)
				// the row shows its selection tint at once, the strip only after the lift
				listState.selectionActive && !pressHeld -> {
					// the read lands on every recomposition, so a settings change reaches the next selection
					val config = ActionConfig.load()
					SelectionActionBar(
						actions = config.bar(searching),
						singleSelected = listState.singleSelected,
						onAction = viewModel::onSelectionAction,
						exportEntries = exportMenu,
						onExport = viewModel::onMenuItemClick,
						overflowEntries = selectionOverflowMenu(
							config, searching, listState.singleSelected, exportBlockedReason,
						),
						onOverflow = viewModel::onMenuItemClick,
						exportBlockedReason = exportBlockedReason,
					)
				}
				else -> {}
			}
		}
		SubjectsFab(
			viewModel, listState, state, fabVisibility, Modifier.matchParentSize())
	}
}

/** The row intents, where hits carry their track path and a selection tap toggles. */
private fun mainCallbacks(
	viewModel: MainExplorerViewModel,
	selectionActive: Boolean,
	onPressHeld: (Boolean) -> Unit,
): ItemListCallbacks =
	ItemListCallbacks(
		onItemClick = { ui ->
			if (selectionActive) viewModel.setSelected(ui.payload, !ui.selected)
			else if (ui.trackPath != null) viewModel.onSearchHitClick(ui)
			else viewModel.onItemClick(ui.payload)
		},
		onItemDoubleTap = viewModel::onItemDoubleTap,
		onItemIconClick = { ui -> viewModel.toggleSelect(ui.payload) },
		onItemLongClick = { ui ->
			viewModel.onItemLongClick(ui.payload)
		},
		onInfo = { ui -> MessagePopup(ui.description) },
		onReference = viewModel::onReferenceClick,
		onInnerInfo = { _, line -> MessagePopup(line.description) },
		onSelectRange = viewModel::selectRange,
		onSelectEnd = viewModel::endRangeSelect,
		onReorderMove = viewModel::moveRow,
		onReorderDrop = viewModel::persistOrder,
		onTopPull = viewModel::openSearchField,
		onPressHeld = onPressHeld,
	)
