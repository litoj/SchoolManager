package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry

/**
 * Menu kind for shown content. Search list takes the search menu — depth picks the browse kind.
 */
fun menuKindFor(searching: Boolean, pathSize: Int): MainExplorerViewModel.MenuKind = when {
	searching -> MainExplorerViewModel.MenuKind.SEARCH
	pathSize == 0 -> MainExplorerViewModel.MenuKind.ROOT
	else -> MainExplorerViewModel.MenuKind.CONTAINER
}

/**
 * More-options menu. [singleSelected] mutes go-to-location unless single.
 */
fun subjectsMenu(
	kind: MainExplorerViewModel.MenuKind,
	selecting: Boolean,
	singleSelected: Boolean = false,
): List<MenuEntry> {
	val menu = when (kind) {
		MainExplorerViewModel.MenuKind.ROOT -> rootMenu
		MainExplorerViewModel.MenuKind.CONTAINER -> containerMenu
		MainExplorerViewModel.MenuKind.SEARCH -> searchMenu(singleSelected)
	}
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

private val exportMenu = listOf(
	MenuEntry(MenuId.EXPORT_WORD, R.string.more_export_word),
	MenuEntry(MenuId.EXPORT_JSON, R.string.more_export_json),
)

private val newMenu = listOf(
	MenuEntry(MenuId.NEW_CHAPTER, R.string.hierarchy_ch),
	MenuEntry(MenuId.NEW_WORD, R.string.hierarchy_word),
	MenuEntry(MenuId.NEW_NOTE, R.string.hierarchy_note),
)

private val importMenu = listOf(
	MenuEntry(MenuId.IMPORT_DIR, R.string.dir_import),
	MenuEntry(MenuId.IMPORT_FILE, R.string.more_import_file),
)

private val rootMenu = listOf(
	MenuEntry(MenuId.NEW_SUBJECT, R.string.new_mch),
	MenuEntry(0, R.string.sort, sortMenu),
	MenuEntry(0, R.string.more_import, importMenu),
	MenuEntry(0, R.string.more_export, exportMenu, always = true),
	MenuEntry(MenuId.FLIP, R.string.more_flip, selectionAction = true),
	MenuEntry(MenuId.REFERENCE, R.string.more_reference, selectionAction = true),
)

private val containerMenu = listOf(
	MenuEntry(0, R.string.more_new, newMenu),
	MenuEntry(0, R.string.sort, sortMenu),
	// Single import needs no submenu.
	MenuEntry(MenuId.IMPORT_FILE, R.string.more_import),
	MenuEntry(0, R.string.more_export, exportMenu, always = true),
	MenuEntry(MenuId.FLIP, R.string.more_flip, selectionAction = true),
	MenuEntry(MenuId.REFERENCE, R.string.more_reference, selectionAction = true),
)


private fun searchMenu(goToEnabled: Boolean) = listOf(
	MenuEntry(MenuId.GO_TO, R.string.more_go_to, selectionAction = true, enabled = goToEnabled),
	MenuEntry(MenuId.FLIP, R.string.more_flip, selectionAction = true),
	MenuEntry(MenuId.REFERENCE, R.string.more_reference, selectionAction = true),
	MenuEntry(0, R.string.more_export, exportMenu, always = true),
	// No sort-default — search has no browse order.
	MenuEntry(0, R.string.sort, sortMenu.filter { it.id != MenuId.SORT_DEFAULT }),
)
