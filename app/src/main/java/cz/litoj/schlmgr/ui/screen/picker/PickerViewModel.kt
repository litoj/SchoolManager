package cz.litoj.schlmgr.ui.screen.picker

import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The test-source picker: the explorer core that selects like the subjects screen.
 * - it starts at the position of the main explorer, on a copy it navigates alone.
 * - a test pick fills the source list, a reference pick links into its initial chapter. */
class PickerViewModel(
	start: NavigationHistory,
	io: CoroutineDispatcher = Dispatchers.IO,
	private val referenceTarget: DbItem? = null,
) : ExplorerViewModel<PickerViewModel.Request>(NavigationHistory(start), picker = true, io = io) {

	/** True when the pick wrote references, so the source screen refreshes only then. */
	var didReference = false
		private set

	sealed interface Request {
		data object Close : Request
	}

	// starts at the copied explorer position, the root included
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
			if (referenceTarget != null) {
				val accepted = withContext(io) {
					ItemRepository.referenceAll(picked.map { it.item.id }, referenceTarget.id)
				}
				if (!accepted) {
					UserMessages.showNotice(UiNotice.PasteInsideItself)
					return@launch
				}
				didReference = picked.isNotEmpty()
				emitRequest(Request.Close)
				return@launch
			}
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

	/** The menu rows of the shared door, the sort family answers in the core. */
	fun onMenuItemClick(itemId: Int): Boolean = when (itemId) {
		MenuId.CLEAR_FILTER -> { clearFilters(); true }
		else -> sortItem(itemId)
	}

	/** Leaving the picker does not exit the app, so one press is enough. */
	override fun registerExitPress() = false

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
}
