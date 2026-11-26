package cz.litoj.schlmgr.ui.screen.picker

import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.ItemTransferService
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestRunData
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
	settings: Settings? = null,
	private val userMessages: UserMessages,
	graphs: GraphQueries? = null,
	private val transfer: ItemTransferService? = null,
	private val testSetup: TestSetupState = TestSetupState.shared,
) : ExplorerViewModel<PickerViewModel.Request>(
	NavigationHistory(start), picker = true, io = io, settings = settings, graphs = graphs,
) {

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

	/** Test sources come with their chapter, so a held chapter and its rows stand for each other. */
	override fun clumpsContainer() = referenceTarget == null

	/**
	 * A test pick lists only the words the rate limit of the setup lets into the test.
	 * - chapters stay, since they lead to the words and the test filters their content too.
	 */
	override fun showsRow(item: DbItem) = referenceTarget != null || item.type != ItemKind.WORD ||
		TestEngine.withinMaxRate(item)

	/**
	 * The strip Select: collects the gathered rows into the source list of the test.
	 * - it closes the picker, and a refused pick reports and stays open.
	 * - chain building reads the database, so it runs on IO.
	 * - the close follows on main once the sources land.
	 */
	fun onSelect() {
		// a hit carries its own chain, a browse row rebuilds it from the parent it was picked under.
		// - read on the main thread, before the dispatcher switch
		val picked = gatheredRowsForClose().map(testSetup::seedOf)
		viewModelScope.launch {
			if (referenceTarget != null) {
				val accepted = withContext(io) {
					checkNotNull(transfer).referenceAll(picked.map { it.item.id }, referenceTarget.id)
				}
				if (!accepted) {
					userMessages.showNotice(UiNotice.PasteInsideItself)
					return@launch
				}
				didReference = picked.isNotEmpty()
				emitRequest(Request.Close)
				return@launch
			}
			val refused = withContext(io) {
				val graphs = checkNotNull(graphs)
				val parentPaths = picked.mapNotNull { it.parentId }.distinct().associateWith { graphs.pathTo(it) }
				val entries = picked.associate { seed ->
					val chain = testSetup.chainOfSeed(seed, parentPaths)
					chain to TestRunData.readEntries(listOf(chain), graphs, limit = 100)
				}
				testSetup.addPicked(picked, parentPaths, entries)
			}
			if (refused.isNotEmpty()) {
				userMessages.showNotice(UiNotice.OneSubjectOnly(testSetup.pickedSubject()?.name))
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
