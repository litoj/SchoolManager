package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.label
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
	const val EXPORT = 7
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
	const val FILTER_NAME = 26
	const val FILTER_TYPE = 27
	const val FILTER_RATE = 28
	const val FILTER_TESTED = 29
	const val SELECT_ALL = 30
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
 * @param searchBlockedReason the reason a search can't run, which mutes the filter too
 * @param selectAllBlockedReason the reason select all can't run, 0 when it can
 */
fun explorerMenu(
	kind: ExplorerMenuKind,
	selecting: Boolean,
	singleSelected: Boolean,
	config: ActionConfig,
	exportBlockedReason: Int = 0,
	filterActive: Boolean = false,
	direct: Int = 4,
	searchBlockedReason: Int = 0,
	selectAllBlockedReason: Int = 0,
	/** False for a host that marks no rows, so the entry never shows. */
	selectAll: Boolean = true,
): List<MenuEntry> {
	val search = kind == ExplorerMenuKind.SEARCH
	val actions = selectionOverflowMenu(config, search, singleSelected, exportBlockedReason, direct)
	val clear = if (search && filterActive)
		listOf(MenuEntry(MenuId.CLEAR_FILTER, R.string.clear_filter, icon = R.drawable.ic_cancel))
	else emptyList()
	val selectAllEntry = MenuEntry(
		MenuId.SELECT_ALL, R.string.select_all, icon = R.drawable.ic_check_all,
		blockedReason = selectAllBlockedReason,
	)
	val selectAllLead = if (selectAll) listOf(selectAllEntry) else emptyList()
	val lead = selectAllLead + (if (search) searchStatic else browseRows()) + filterMenu(searchBlockedReason)
	val menu = if (search) lead + clear + actions else lead + actions
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

/** The browse menu rows, where import belongs to the New palette instead. */
private fun browseRows(): List<MenuEntry> = listOf(
	MenuEntry(0, R.string.sort, icon = R.drawable.ic_sort_black, children = sortMenu),
)

/**
 * The filter tags of the header, the least used one stays in the editor.
 * - a tag pick opens the editor on that tag, so the success rate shows before any search.
 */
private val filterTags = linkedMapOf(
	MenuId.FILTER_NAME to SearchTag.NAME,
	MenuId.FILTER_TYPE to SearchTag.TYPE,
	MenuId.FILTER_RATE to SearchTag.RATE,
	MenuId.FILTER_TESTED to SearchTag.TESTED,
)

/** The tag of one filter leaf id, null for any other id. */
fun filterTagOf(id: Int): SearchTag? = filterTags[id]

/** The filter icon of the header, which a blocked search mutes with the same reason. */
private fun filterMenu(searchBlockedReason: Int) = MenuEntry(
	0, R.string.filter_tag, icon = R.drawable.ic_filter,
	children = filterTags.map { (id, tag) -> MenuEntry(id, tag.label) },
	blockedReason = searchBlockedReason,
)

private val searchStatic = listOf(
	// no sort-default, since search has no browse order.
	MenuEntry(0, R.string.sort, icon = R.drawable.ic_sort_black,
		children = sortMenu.filter { it.id != MenuId.SORT_DEFAULT }),
)

/** One overflowed action as a menu row, every action one leaf of its own. */
private fun SelectionAction.menuEntry(
	singleSelected: Boolean,
	exportBlockedReason: Int,
): MenuEntry = MenuEntry(
	menuIdOf(this),
	label,
	icon = icon,
	selectionAction = true,
	blockedReason = when (this) {
		SelectionAction.EDIT, SelectionAction.GOTO ->
			if (singleSelected) 0 else R.string.unavailable_single_row
		// the sheet only fills with a selection that has a line in the format
		SelectionAction.EXPORT -> exportBlockedReason
		else -> 0
	},
)

/** The more-options leaf id of one action. */
fun menuIdOf(action: SelectionAction): Int = when (action) {
	SelectionAction.TEST -> MenuId.TEST
	SelectionAction.CUT -> MenuId.CUT
	SelectionAction.EDIT -> MenuId.EDIT
	SelectionAction.DELETE -> MenuId.DELETE
	SelectionAction.REFERENCE -> MenuId.REFERENCE
	SelectionAction.FLIP -> MenuId.FLIP
	SelectionAction.GOTO -> MenuId.GOTO
	SelectionAction.EXPORT -> MenuId.EXPORT
}
