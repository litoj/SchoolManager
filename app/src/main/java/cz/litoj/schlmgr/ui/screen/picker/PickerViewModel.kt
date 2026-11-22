package cz.litoj.schlmgr.ui.screen.picker

import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The test-source picker: the explorer core that gathers like an armed paste.
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

	/** The picks gather across folders, so one Select takes rows of several. */
	override fun gathering() = true

	/**
	 * The strip Select: collects the gathered rows into the source list of the test.
	 * - it closes the picker, and a refused pick reports and stays open.
	 * - chain building reads the database, so it runs on IO.
	 * - the close follows on main once the sources land.
	 */
	fun onSelect() {
		// a hit carries its own chain, a browse row rebuilds it from the parent it was picked under.
		// - read on the main thread, before the dispatcher switch
		val picked = batchRows().map(TestSetupState::seedOf)
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

	/** Leaving the picker does not exit the app, so one press is enough. */
	override fun registerExitPress() = false
}
