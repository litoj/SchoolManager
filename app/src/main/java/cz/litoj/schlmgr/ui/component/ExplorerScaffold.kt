package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	/** The list state of the owning screen, shared so the screen can watch the scroll. */
	scrollState: LazyListState = rememberLazyListState(),
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
	actionBar: @Composable () -> Unit = {},
) {
	// the displayed list, browse content and search results alike, comes from
	// the caller. The scaffold renders it as-is, with no search side channel.
	Column(modifier.fillMaxSize()) {
		ExplorerTopBar(
			breadcrumbs = state.breadcrumbs,
			onBreadcrumbClick = onBreadcrumbClick,
			info = state.info,
			isInfoExpanded = state.isInfoExpanded,
			onInfoClick = onInfoClick,
		)
		listArea()
		actionBar()
	}
}
