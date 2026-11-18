package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState
import cz.litoj.schlmgr.ui.viewmodel.ItemListState

/**
 * The explorer screen: top chrome and item list, plus a slot for the bottom action strip.
 * - stateless, the screen collects the state and maps row intents through [callbacks].
 * - [listArea] overrides the plain list, the sibling slide of the subjects screen does it.
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
	// the anchor lands before the slide composes the incoming pane, so it shows in place
	LaunchedEffect(listState.anchor) {
		listState.anchor?.let { scrollState.scrollToItem(it.index, it.offset) }
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
	Column(modifier.fillMaxSize()) {
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
