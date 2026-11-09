package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.icon
import cz.litoj.schlmgr.ui.viewmodel.label

/**
 * The menu kind for the shown content.
 * - both browse kinds share the menu, the FAB palette tells them apart.
 */
fun menuKindFor(searching: Boolean, pathSize: Int): MainExplorerViewModel.MenuKind = when {
	searching -> MainExplorerViewModel.MenuKind.SEARCH
	pathSize == 0 -> MainExplorerViewModel.MenuKind.ROOT
	else -> MainExplorerViewModel.MenuKind.CONTAINER
}

/**
 * The overflow actions of one list kind as menu rows.
 * - the strip shares this list through its More slot, so both places show one order.
 */
internal fun selectionOverflowMenu(
	config: ActionConfig,
	search: Boolean,
	singleSelected: Boolean,
	exportBlockedReason: Int,
): List<MenuEntry> =
	config.overflow(search).map { it.menuEntry(singleSelected, exportBlockedReason) }

/**
 * The more menu of one list kind.
 * - the selection actions come from the config overflow, in the picked order.
 * - edit and go-to act on one row, so a wider selection mutes them with a reason.
 * - an export of translations only stays in place with its reason.
 *
 * @param exportBlockedReason the reason of an empty writable selection, 0 when it can export
 * @param filterActive whether the search runs a structured filter, which adds its clear row
 */
fun subjectsMenu(
	kind: MainExplorerViewModel.MenuKind,
	selecting: Boolean,
	singleSelected: Boolean,
	config: ActionConfig,
	exportBlockedReason: Int = 0,
	filterActive: Boolean = false,
): List<MenuEntry> {
	val search = kind == MainExplorerViewModel.MenuKind.SEARCH
	val actions = selectionOverflowMenu(config, search, singleSelected, exportBlockedReason)
	val clear = if (search && filterActive)
		listOf(MenuEntry(MenuId.CLEAR_FILTER, R.string.clear_filter, icon = R.drawable.ic_cancel))
	else emptyList()
	val menu = if (search) searchStatic + clear + actions else browseStatic + actions
	return menu.filter { it.always || it.selectionAction == selecting }
}

private val sortMenu = listOf(
	MenuEntry(MenuId.SORT_ALPHA_AZ, R.string.sort_alpha_AZ),
	MenuEntry(MenuId.SORT_ALPHA_ZA, R.string.sort_alpha_ZA),
	MenuEntry(MenuId.SORT_SF_01, R.string.sort_sf_01),
	MenuEntry(MenuId.SORT_SF_10, R.string.sort_sf_10),
	MenuEntry(MenuId.SORT_PRIORITY, R.string.sort_priority),
	MenuEntry(MenuId.SORT_LENGTH_01, R.string.sort_length_01),
	MenuEntry(MenuId.SORT_LENGTH_10, R.string.sort_length_10),
	MenuEntry(MenuId.SORT_DEFAULT, R.string.sort_default),
)

/** The export format chooser, shared by the menu parent and the strip slot. */
internal val exportMenu = listOf(
	MenuEntry(MenuId.EXPORT_WORD, R.string.more_export_word),
	MenuEntry(MenuId.EXPORT_JSON, R.string.more_export_json),
)

private val browseStatic = listOf(
	MenuEntry(MenuId.SELECT, R.string.select_rows, icon = R.drawable.ic_select_black),
	MenuEntry(0, R.string.sort, icon = R.drawable.ic_sort_black, children = sortMenu),
	MenuEntry(MenuId.IMPORT_FILE, R.string.more_import, icon = R.drawable.ic_import_black),
)

private val searchStatic = listOf(
	MenuEntry(MenuId.SELECT, R.string.select_rows, icon = R.drawable.ic_select_black),
	// no sort-default, since search has no browse order.
	MenuEntry(0, R.string.sort, icon = R.drawable.ic_sort_black,
		children = sortMenu.filter { it.id != MenuId.SORT_DEFAULT }),
)

/** One overflowed action as a menu row. */
private fun SelectionAction.menuEntry(
	singleSelected: Boolean,
	exportBlockedReason: Int,
): MenuEntry =
	if (this == SelectionAction.EXPORT) MenuEntry(
		0, R.string.more_export, icon = R.drawable.ic_export_black, children = exportMenu,
		selectionAction = true, blockedReason = exportBlockedReason,
	)
	else MenuEntry(
		menuIdOf(this),
		label,
		icon = icon,
		selectionAction = true,
		blockedReason = when (this) {
			SelectionAction.EDIT, SelectionAction.GOTO ->
				if (singleSelected) 0 else R.string.unavailable_single_row
			else -> 0
		},
	)

/** The more-options leaf id of one action, where the export submenu builds its own parent. */
fun menuIdOf(action: SelectionAction): Int = when (action) {
	SelectionAction.TEST -> MenuId.TEST
	SelectionAction.CUT -> MenuId.CUT
	SelectionAction.EDIT -> MenuId.EDIT
	SelectionAction.DELETE -> MenuId.DELETE
	SelectionAction.REFERENCE -> MenuId.REFERENCE
	SelectionAction.FLIP -> MenuId.FLIP
	SelectionAction.GOTO -> MenuId.GOTO
	SelectionAction.EXPORT -> MenuId.EXPORT_WORD
}
