package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import cz.litoj.schlmgr.ui.component.NewItemFab
import cz.litoj.schlmgr.ui.component.rememberScrollUpVisible
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * The subjects explorer's floating New button — the screen's half of the
 * wiring: which kinds the open screen offers and where the pick
 * dispatches. The button and its palette are [NewItemFab].
 */
@Composable
internal fun SubjectsFab(
	viewModel: MainExplorerViewModel,
	listState: ItemListState,
	state: ExplorerScreenState,
	scrollState: LazyListState,
	modifier: Modifier = Modifier,
) {
	NewItemFab(
		kinds = newKinds(
			listState.selectionActive, viewModel.pasteArmed,
			menuKindFor(listState.variant == ExplorerRowVariant.Search, state.breadcrumbs.size)),
		visible = rememberScrollUpVisible(scrollState),
		onPick = { kind -> viewModel.onMenuItemClick(newMenuIdOf(kind)) },
		modifier = modifier,
	)
}

/** The kinds the floating New button offers — none hides it. */
private fun newKinds(
	selecting: Boolean,
	pasteArmed: Boolean,
	kind: MainExplorerViewModel.MenuKind,
): List<RowKind> {
	// the strip owns the bottom while a selection or a paste is armed
	if (selecting || pasteArmed) return emptyList()
	return when (kind) {
		MainExplorerViewModel.MenuKind.ROOT -> listOf(RowKind.SUBJECT)
		MainExplorerViewModel.MenuKind.CONTAINER -> listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE)
		// the New actions refuse while searching
		MainExplorerViewModel.MenuKind.SEARCH -> emptyList()
	}
}

/** The New-menu id the picked kind dispatches through. */
private fun newMenuIdOf(kind: RowKind): Int = when (kind) {
	RowKind.SUBJECT -> MenuId.NEW_SUBJECT
	RowKind.CHAPTER -> MenuId.NEW_CHAPTER
	RowKind.WORD -> MenuId.NEW_WORD
	RowKind.NOTE -> MenuId.NEW_NOTE
}
