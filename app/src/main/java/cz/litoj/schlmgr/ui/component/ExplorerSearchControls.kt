package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.TopBarActions
import cz.litoj.schlmgr.ui.shell.TopBarEntryAction
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarTitle

/**
 * The shared search and overflow controls of an explorer header.
 * - the one pinned icon is search while closed and the filter parent while the field is open.
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
	onMenuPick: (Int) -> Unit,
) {
	if (fieldOpen) TopBarSearchField(
		query, onQueryChange, onQuerySubmit, onDismissSearch,
		Modifier.weight(1f),
	)
	else TopBarTitle(title)
	val filter = entries.single { it.label == R.string.filter_tag }
	if (fieldOpen) TopBarEntryAction(filter, onMenuPick)
	else TopBarIconButton(
		icon = R.drawable.ic_search,
		contentDescription = stringResource(R.string.search),
		enabled = searchBlockedReason == 0,
		blockedReason = searchBlockedReason,
		onClick = onOpenSearch,
	)
	val unpinned = if (fieldOpen) entries.filterNot { it === filter } else entries
	TopBarActions(unpinned, width, onMenuPick, pinned = 1)
}
