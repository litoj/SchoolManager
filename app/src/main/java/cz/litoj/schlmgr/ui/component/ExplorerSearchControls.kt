package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.TopBarActions
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarTitle

/**
 * The shared search and overflow controls of an explorer header.
 * - their order never changes: search, the list actions, then the pinned select-all.
 * - the list actions stay while a selection runs, since the strip only owns the row actions.
 */
@Composable
fun RowScope.ExplorerSearchControls(
	title: String,
	query: String,
	fieldOpen: Boolean,
	searchBlockedReason: Int,
	entries: List<MenuEntry>,
	width: Dp,
	onOpenSearch: () -> Unit,
	onQueryChange: (String) -> Unit,
	onQuerySubmit: (String) -> Unit,
	onDismissSearch: () -> Unit,
	onFilter: () -> Unit,
	onToggleSelectAll: () -> Unit,
	onMenuPick: (Int) -> Unit,
) {
	if (fieldOpen) TopBarSearchField(
		query, onQueryChange, onQuerySubmit, onDismissSearch,
		Modifier.weight(1f), onFilter = onFilter,
	)
	else TopBarTitle(title)
	TopBarIconButton(
		icon = R.drawable.ic_search,
		contentDescription = stringResource(R.string.search),
		visible = !fieldOpen,
		enabled = searchBlockedReason == 0,
		blockedReason = searchBlockedReason,
		onClick = onOpenSearch,
	)
	TopBarActions(entries, width, onMenuPick, pinned = 2)
	TopBarIconButton(
		icon = R.drawable.ic_check_all,
		contentDescription = stringResource(R.string.select_all),
		onClick = onToggleSelectAll,
	)
}
