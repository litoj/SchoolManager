package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.litoj.schlmgr.ui.component.FabVisibility
import cz.litoj.schlmgr.ui.component.NewItemFab
import cz.litoj.schlmgr.ui.viewmodel.ExplorerMenuKind
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * The floating New button of the subjects explorer.
 * - it holds the screen half of the wiring: the kinds the open screen offers.
 * - the same wiring covers where the pick dispatches.
 * - the button and its palette are [NewItemFab].
 */
@Composable
internal fun SubjectsFab(
	viewModel: MainExplorerViewModel,
	listState: ItemListState,
	visibility: FabVisibility,
	modifier: Modifier = Modifier,
) {
	NewItemFab(
		kinds = newKinds(
			listState.selectionActive, viewModel.pasteArmed, viewModel.menuKind),
		visible = visibility.shown,
		onPick = { kind -> viewModel.onMenuItemClick(newMenuIdOf(kind)) },
		onImport = { viewModel.onMenuItemClick(MenuId.IMPORT_FILE) },
		onReference = if (viewModel.menuKind == ExplorerMenuKind.CONTAINER)
			viewModel::pickReferences else null,
		modifier = modifier,
	)
}

/** The kinds the floating New button offers, where none hides it. */
private fun newKinds(
	selecting: Boolean,
	pasteArmed: Boolean,
	kind: ExplorerMenuKind,
): List<RowKind> {
	// the strip owns the bottom while a selection or a paste is armed
	if (selecting || pasteArmed) return emptyList()
	return when (kind) {
		ExplorerMenuKind.ROOT -> listOf(RowKind.SUBJECT)
		ExplorerMenuKind.CONTAINER -> listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE)
		// the New actions refuse while searching
		ExplorerMenuKind.SEARCH -> emptyList()
	}
}

private fun newMenuIdOf(kind: RowKind): Int = when (kind) {
	RowKind.SUBJECT -> MenuId.NEW_SUBJECT
	RowKind.CHAPTER -> MenuId.NEW_CHAPTER
	RowKind.WORD -> MenuId.NEW_WORD
	RowKind.NOTE -> MenuId.NEW_NOTE
}
