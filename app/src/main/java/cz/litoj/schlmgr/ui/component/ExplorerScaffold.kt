package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState

/**
 * The complete explorer screen = the legacy `explorer.xml` content (top chrome + item
 * list) plus a slot for whichever bottom action-strip the host needs. Driven entirely
 * by an [ExplorerViewModel]; the scaffold collects its [ExplorerScreenState] and maps
 * the row intents back onto the host-supplied [callbacks].
 *
 * @param actionBar the host's bottom strip ([SelectionActionBar]/[PasteActionBar]/
 *   [PickerActionBar] or empty); the scaffold places it under the list.
 */
@Composable
fun ExplorerScaffold(
	viewModel: ExplorerViewModel,
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	actionBar: @Composable () -> Unit = {},
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	// The displayed list — browse content and search results alike — comes from
	// the ViewModel; the scaffold renders it as-is, no search side channel.
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	Column(modifier.fillMaxSize()) {
		ExplorerTopBar(
			breadcrumbs = state.breadcrumbs,
			onBreadcrumbClick = viewModel::breadcrumbClick,
			infoText = state.infoText,
			isInfoExpanded = state.isInfoExpanded,
			onInfoClick = viewModel::toggleInfoExpanded,
		)
		ItemList(
			state = listState,
			callbacks = callbacks,
			modifier = Modifier.weight(1f),
			reorderable = state.reorderable,
		)
		actionBar()
	}
}
