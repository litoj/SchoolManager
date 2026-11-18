package cz.litoj.schlmgr.ui.shell

import cz.litoj.schlmgr.ui.popup.CriterionPopup
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel

/**
 * The editor of one filter of [host], shared by the filter icon and the chips.
 * - a null criterion opens a new filter, the chip of one opens that filter.
 */
fun editFilter(host: ExplorerViewModel<*>, criterion: Criterion?) {
	CriterionPopup(host.searchQuery(), criterion, host::applyFilter, host::dropFilter)
}
