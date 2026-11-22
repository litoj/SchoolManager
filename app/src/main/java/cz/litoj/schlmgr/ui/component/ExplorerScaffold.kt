package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState
import cz.litoj.schlmgr.ui.viewmodel.ItemListState

/**
 * The explorer screen: top chrome and item list, plus a slot for the bottom action strip.
 * - stateless, the screen collects the state and maps row intents through [callbacks].
 * - the sibling swipe rides the frame, so every explorer switches the same way.
 * - [listArea] overrides the plain list, a host that composes its own list does it.
 */
@Composable
fun ExplorerScaffold(
	state: ExplorerScreenState,
	listState: ItemListState,
	onBreadcrumbClick: (Int) -> Unit,
	onInfoClick: () -> Unit,
	onFilterEdit: (Criterion) -> Unit,
	onFilterDrop: (SearchTag) -> Unit,
	onParentClick: () -> Unit,
	callbacks: ItemListCallbacks,
	/** The sibling step the horizontal swipe asks for, one ahead or one back. */
	onSibling: (Int) -> Unit,
	modifier: Modifier = Modifier,
	/** The list state of the owning screen, shared so the screen can watch the scroll. */
	scrollState: LazyListState = rememberLazyListState(),
	/** The live scroll of the list, which every history snapshot captures on leaving. */
	onScrollPosition: (Int, Int) -> Unit = { _, _ -> },
	/** The list area rides in the column, so it can weight itself against the strip. */
	listArea: @Composable ColumnScope.() -> Unit = {
		ItemList(
			state = listState,
			callbacks = callbacks,
			modifier = Modifier.weight(1f),
			reorderable = state.reorderable,
			listState = scrollState,
		)
	},
	/** A transient line that replaces the info strip, such as an armed move. */
	statusLine: String? = null,
	actionBar: @Composable () -> Unit = {},
) {
	val currentReport by rememberUpdatedState(onScrollPosition)
	// the request preserves this state and performs no animated correction
	SideEffect(scrollState, listState.anchor) {
		val anchor = listState.anchor ?: return@SideEffect
		// the same position must not invalidate the layout and spend another frame on it
		if (scrollState.firstVisibleItemIndex == anchor.index &&
			scrollState.firstVisibleItemScrollOffset == anchor.offset
		) return@SideEffect
		scrollState.requestScrollToItem(anchor.index, anchor.offset)
	}
	ListEndReveal(listState, scrollState)
	// the list keeps the one live scroll, which every history snapshot captures on leaving
	LaunchedEffect(scrollState) {
		snapshotFlow {
			scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
		}.collect { (index, offset) -> currentReport(index, offset) }
	}
	// the displayed list, browse content and search results alike, comes from
	// the caller. The scaffold renders it as-is, with no search side channel.
	Column(modifier.fillMaxSize().siblingSwipe(onSibling)) {
		ExplorerTopBar(
			breadcrumbs = state.breadcrumbs,
			onBreadcrumbClick = onBreadcrumbClick,
			info = state.info,
			isInfoExpanded = state.isInfoExpanded,
			onInfoClick = onInfoClick,
			onFilterEdit = onFilterEdit,
			onFilterDrop = onFilterDrop,
			onParentClick = onParentClick,
			statusLine = statusLine,
		)
		listArea()
		actionBar()
	}
}

/** The pixel travel that switches a sibling, so a short drag keeps the list. */
private val SiblingSwipeThreshold = 40.dp

/** The horizontal swipe of the explorer frame, which asks for one sibling step. */
@Composable
private fun Modifier.siblingSwipe(onSibling: (Int) -> Unit): Modifier {
	val threshold = with(LocalDensity.current) { SiblingSwipeThreshold.toPx() }
	val current by rememberUpdatedState(onSibling)
	val swipe = remember { SiblingSwipe() }
	// the pixel threshold keys the gesture, so a density change restarts it
	return pointerInput(threshold) {
		detectHorizontalDragGestures(
			onHorizontalDrag = { _, amount -> swipe.travel += amount },
			onDragEnd = {
				if (swipe.travel < -threshold) current(1)
				else if (swipe.travel > threshold) current(-1)
				swipe.reset()
			},
			onDragCancel = { swipe.reset() },
		)
	}
}

/** The travel of the running swipe, so a drag that ends short changes nothing. */
private class SiblingSwipe {
	var travel = 0f

	fun reset() {
		travel = 0f
	}
}
