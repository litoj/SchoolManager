package cz.litoj.schlmgr.ui.screen.picker

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The test-source picker: the explorer core in permanent selection mode.
 * - it adds the confirm to the source list.
 */
class PickerViewModel(
	io: CoroutineDispatcher = Dispatchers.IO,
) : ExplorerViewModel<PickerViewModel.Request>(ensureHistory(), picker = true, io = io) {

	sealed interface Request {
		data object Close : Request
	}

	companion object {
		/**
		 * The own [NavigationHistory] of the picker, kept across screens.
		 * - so re-entering the picker restores the previous position.
		 */
		private var savedHistory: NavigationHistory? = null

		private fun ensureHistory(): NavigationHistory =
			savedHistory ?: NavigationHistory().also { savedHistory = it }

		/** Drops the saved position, so the next picker starts at the root. */
		@VisibleForTesting
		@JvmStatic
		internal fun resetSavedHistory() {
			savedHistory = null
		}
	}

	// the model is created at the browsing position the saved history holds.
	init {
		setContent(history.path.lastOrNull())
	}

	/**
	 * The strip Select: collects the selected rows into the source list of the test.
	 * - it closes the picker, and a refused pick reports and stays open.
	 * - chain building reads the database, so it runs on IO.
	 * - the close follows on main once the sources land.
	 */
	fun onSelect() {
		// browse rows and search-hit rows share the live models.
		// - a hit carries its own chain.
		// - a browse row rebuilds it from the parent it was selected under.
		// - read on the main thread, before the dispatcher switch
		val picked = items().filter(ItemRow::selected).map(TestSetupState::seedOf)
		viewModelScope.launch {
			val refused = withContext(io) { TestSetupState.addPicked(picked) }
			if (refused.isNotEmpty()) {
				UserMessages.showNotice(UiNotice.OneSubjectOnly(TestSetupState.pickedSubject()?.name))
				return@launch
			}
			emitRequest(Request.Close)
		}
	}

	fun cancel() {
		emitRequest(Request.Close)
	}

	/** Flip a word, otherwise open the container under the parent it was displayed in. */
	fun onItemClick(row: ItemRow?) {
		row ?: return
		val item = row.item
		if (item.type == ItemKind.WORD) {
			flipWord(row)
			return
		}
		if (item.type != ItemKind.CHAPTER) return
		if (!searching) {
			history.hop(item)
			setContent(item)
		} else {
			// a search row opens through the chain of its hit
			val path = mutableListOf<DbItem>()
			path.addAll(row.path ?: emptyList())
			path.add(item)
			openChain(path)
		}
		onChange()
	}

	fun onItemLongClick(row: ItemRow?) {
		toggleSelect(row)
	}
}
