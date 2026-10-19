package cz.litoj.schlmgr.support

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers

/**
 * A concrete [ExplorerViewModel] for the tests that exercise the list behaviour
 * only — the screen's content switch stays inert, so no database and no screen
 * is needed. SearchModeLadderTest derives from it to watch its content switch,
 * with repository reads run inline on an unconfined dispatcher.
 */
internal open class TestExplorerViewModel(history: NavigationHistory) :
	ExplorerViewModel<Nothing>(history, io = Dispatchers.Unconfined) {
	protected override fun setContent(container: DbItem?) {}
}

/** Word rows for a list-only test — no database behind them, so a load stays pure. */
internal fun wordRows(count: Int): List<ItemRow> =
	(0 until count).map { i ->
		ItemRow(DbItem(name = "w$i", description = "", type = ItemKind.WORD), null, i + 1, false)
	}

/** A list-only view model loaded with [count] word rows. */
internal fun wordVm(count: Int): TestExplorerViewModel =
	TestExplorerViewModel(NavigationHistory()).also { vm -> vm.load(wordRows(count)) }
