package cz.litoj.schlmgr.ui.shell

import cz.litoj.schlmgr.ui.popup.CriterionPopup
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.filterTagOf

/**
 * The editor of one filter of [host], shared by the filter chips and the header menu.
 * - a null criterion opens a new filter on [tag], a set one opens that filter.
 */
fun editFilter(host: ExplorerViewModel<*>, criterion: Criterion?, tag: SearchTag? = null) {
	CriterionPopup(host.searchQuery(), criterion, host::applyFilter, host::dropFilter, tag)
}

/**
 * The header menu door of [host], where a filter tag opens the editor.
 * - a tag with a set filter opens that filter, so a pick never stacks a second one.
 */
fun pickExplorerMenu(host: ExplorerViewModel<*>, id: Int) {
	val tag = filterTagOf(id) ?: run { host.onMenuItemClick(id); return }
	editFilter(host, host.searchQuery().criterionOf(tag), tag)
}
