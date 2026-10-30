package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PasteActionBar
import cz.litoj.schlmgr.ui.component.SelectionActionBar
import cz.litoj.schlmgr.ui.component.fabDragVisibility
import cz.litoj.schlmgr.ui.component.ListEndReveal
import cz.litoj.schlmgr.ui.component.rememberFabVisibility
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant

/**
 * The explorer content of the subjects screen.
 * - the scaffold holds the action strip and the floating New button.
 * - the sibling swipe sits over all of it.
 */
@Composable
internal fun SubjectsContent(viewModel: MainExplorerViewModel, modifier: Modifier = Modifier) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	// a horizontal swipe switches to the sibling.
	// the pixel threshold keys the gesture, so a density change restarts it
	val swipeThreshold = with(LocalDensity.current) { 40.dp.toPx() }
	val searching = listState.variant == ExplorerRowVariant.Search
	var swipe by remember { mutableFloatStateOf(0f) }
	val openSibling by rememberUpdatedState(viewModel::openSibling)
	val scrollState = rememberLazyListState()
	// the New button visibility follows the drag gesture, not the list scroll
	val fabVisibility = rememberFabVisibility()
	ListEndReveal(listState, scrollState)
	// a load landing resets the button, an insert or delete keeps it hidden
	LaunchedEffect(state.contentStep) { fabVisibility.show() }
	Box(modifier) {
		val callbacks = mainCallbacks(viewModel, listState.selectionActive)
		ExplorerScaffold(
			state = state,
			listState = listState,
			onBreadcrumbClick = viewModel::breadcrumbClick,
			onInfoClick = viewModel::toggleInfoExpanded,
			callbacks = callbacks,
			scrollState = scrollState,
			listArea = { SiblingSlideList(state, listState, callbacks, scrollState, Modifier.weight(1f)) },
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
				listState.selectionActive -> SelectionActionBar(
					editEnabled = listState.singleSelected,
					onTest = viewModel::prepareTest,
					onCut = if (searching) null else ({ viewModel.move(false) }),
					onEdit = viewModel::onEditAction,
					onDelete = viewModel::onDeleteAction,
					onGoTo = if (searching) viewModel::onGoToAction else null,
					goToEnabled = listState.singleSelected,
				)
				else -> {}
			}
		}
		SubjectsFab(
			viewModel, listState, state, fabVisibility, Modifier.matchParentSize())
	}
}

/** The row intents, where hits carry their track path and a selection tap toggles. */
private fun mainCallbacks(viewModel: MainExplorerViewModel, selectionActive: Boolean): ItemListCallbacks =
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
	)
