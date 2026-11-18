package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry

/**
 * The menu form of the open list.
 * - both browse kinds share the menu, the New palette tells them apart.
 */
enum class ExplorerMenuKind { ROOT, CONTAINER, SEARCH }

/**
 * More-options leaf ids, one table for every explorer host.
 * - each host answers the ids its own door offers.
 */
object MenuId {
	const val NEW_SUBJECT = 1
	const val NEW_CHAPTER = 2
	const val NEW_WORD = 3
	const val NEW_NOTE = 4
	const val IMPORT_FILE = 6
	const val EXPORT_WORD = 7
	const val EXPORT_JSON = 8
	const val FLIP = 9
	const val REFERENCE = 10
	const val SORT_ALPHA_AZ = 11
	const val SORT_ALPHA_ZA = 12
	const val SORT_SF_01 = 13
	const val SORT_SF_10 = 14
	const val SORT_LENGTH_01 = 15
	const val SORT_LENGTH_10 = 16
	const val SORT_DEFAULT = 17
	const val SORT_PRIORITY = 19
	const val TEST = 20
	const val CUT = 21
	const val EDIT = 22
	const val GOTO = 23
	const val DELETE = 24
	const val CLEAR_FILTER = 25
}

/**
 * The menu kind of the shown content.
 * - [ExplorerViewModel.menuKind] reads it from the state, so no host picks a kind by hand.
 */
fun menuKindFor(searching: Boolean, pathSize: Int): ExplorerMenuKind = when {
	searching -> ExplorerMenuKind.SEARCH
	pathSize == 0 -> ExplorerMenuKind.ROOT
	else -> ExplorerMenuKind.CONTAINER
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
	direct: Int,
): List<MenuEntry> =
	config.overflow(search, direct).map { it.menuEntry(singleSelected, exportBlockedReason) }

/**
 * The more menu of one list kind, the one door every explorer host walks through.
 * - the selection actions come from the config overflow, in the picked order.
 * - edit and go-to act on one row, so a wider selection mutes them with a reason.
 * - an export of translations only stays in place with its reason.
 *
 * @param exportBlockedReason the reason of an empty writable selection, 0 when it can export
 * @param filterActive whether the search runs a structured filter, which adds its clear row
 */
fun explorerMenu(
	kind: ExplorerMenuKind,
	selecting: Boolean,
	singleSelected: Boolean,
	config: ActionConfig,
	exportBlockedReason: Int = 0,
	filterActive: Boolean = false,
	direct: Int = 4,
): List<MenuEntry> {
	val search = kind == ExplorerMenuKind.SEARCH
	val actions = selectionOverflowMenu(config, search, singleSelected, exportBlockedReason, direct)
	val clear = if (search && filterActive)
		listOf(MenuEntry(MenuId.CLEAR_FILTER, R.string.clear_filter, icon = R.drawable.ic_cancel))
	else emptyList()
	val menu = if (search) searchStatic + clear + actions
	else browseRows() + actions
	return menu.filter { it.selectionAction == selecting }
}

private val sortMenu = listOf(
	MenuEntry(MenuId.SORT_DEFAULT, R.string.sort_default),
	MenuEntry(MenuId.SORT_ALPHA_ZA, R.string.sort_alpha_ZA),
	MenuEntry(MenuId.SORT_ALPHA_AZ, R.string.sort_alpha_AZ),
	MenuEntry(MenuId.SORT_SF_10, R.string.sort_sf_10),
	MenuEntry(MenuId.SORT_SF_01, R.string.sort_sf_01),
	MenuEntry(MenuId.SORT_PRIORITY, R.string.sort_priority),
	MenuEntry(MenuId.SORT_LENGTH_10, R.string.sort_length_10),
	MenuEntry(MenuId.SORT_LENGTH_01, R.string.sort_length_01),
)

/** The export format chooser, shared by the menu parent and the strip slot. */
internal val exportMenu = listOf(
	MenuEntry(MenuId.EXPORT_WORD, R.string.more_export_word),
	MenuEntry(MenuId.EXPORT_JSON, R.string.more_export_json),
)

/** The browse menu rows, where import belongs to the New palette instead. */
private fun browseRows(): List<MenuEntry> = listOf(
	MenuEntry(0, R.string.sort, icon = R.drawable.ic_sort_black, children = sortMenu),
)

private val searchStatic = listOf(
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
