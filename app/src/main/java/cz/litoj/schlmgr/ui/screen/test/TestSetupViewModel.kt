package cz.litoj.schlmgr.ui.screen.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.component.NumberInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The logic of the test setup screen.
 * - it holds the amount, duration and rate answers, the reverse and content switches and the fresh-entry decision.
 * - it also holds the two navigation steps, start test and open the source picker.
 * - answers and sources live in [TestSetupState], whose source doors serialize every writer.
 */
class TestSetupViewModel(
	private val io: CoroutineDispatcher = Dispatchers.IO,
	val testSetup: TestSetupState = TestSetupState.shared,
	private val settings: Settings,
	private val userMessages: UserMessages,
) : ViewModel() {

	/**
	 * One step that only the view layer can perform for this screen.
	 * - it starts the running-test activity or navigates to the source picker.
	 */
	sealed interface Request {
		/** The Start button, which needs non-empty sources, see [startTest]. */
		data object StartTest : Request

		data object OpenPicker : Request
	}

	private val _requests = Channel<Request>(Channel.UNLIMITED)
	val requests = _requests.receiveAsFlow()

	fun setAmount(text: String) {
		testSetup.setAmount(text)
	}

	fun setTime(text: String) {
		testSetup.setTime(text)
	}

	// the count refresh reads the database, so the removal and its undo leave the main thread
	fun removeSource(chain: List<DbItem>) {
		viewModelScope.launch(io) {
			val index = testSetup.removeSource(chain) ?: return@launch
			userMessages.showUndoable(UiNotice.SourceRemoved(chain.toSourceRowUi(testSetup).displayName)) {
				restoreSource(chain, index)
			}
		}
	}

	private fun restoreSource(chain: List<DbItem>, index: Int) {
		viewModelScope.launch(io) {
			if (!testSetup.restoreSource(chain, index))
				userMessages.showNotice(UiNotice.OneSubjectOnly(testSetup.pickedSubject()?.name))
		}
	}

	/** The Clear list action of the sources header. */
	fun clearSources() = testSetup.clearSources()

	/**
	 * The fresh-entry check, run once per entry.
	 * - a return from the picker, the running test or an Add to test keeps the answers.
	 * - a genuine fresh entry resets the answers, the sources stay until cleared.
	 */
	fun onEntered() {
		val (fromPicker, fromSelection) = testSetup.consumeEntryMarks()
		if (!fromPicker && !fromSelection) testSetup.resetAnswers()
	}

	fun restoreAmount() {
		val n = testSetup.amountText.toIntOrNull() ?: 0
		if (n < 1) testSetup.setAmount(TestEngine.amount.toString())
	}

	fun restoreTime() {
		if (NumberInput.amount(testSetup.timeText) == null)
			testSetup.setTime(TestEngine.getSecondsPerChar().toString())
	}

	fun toggleReverse() {
		val on = !testSetup.reverseTest
		testSetup.setReverse(on)
		TestEngine.applyReversed(on)
		settings.set("reverseTest", on)
	}

	/** The content switch: on ranks by urgency, off lets a random pool through. */
	fun toggleClever() {
		val on = !testSetup.cleverTest
		testSetup.setClever(on)
		TestEngine.applyClever(on)
		settings.set("isClever", on)
	}

	/**
	 * Each usable limit applies at once, so the pool caption follows the typing.
	 * - the recount reads the database, so it leaves the main thread.
	 */
	fun setMaxRate(text: String) {
		testSetup.setMaxRate(text)
		val rate = testSetup.maxRateText.toIntOrNull()?.takeIf { it in 0..100 } ?: return
		TestEngine.applyMaxRate(rate)
		viewModelScope.launch(io) { testSetup.refreshCounts() }
	}

	fun restoreMaxRate() {
		if (testSetup.maxRateText.toIntOrNull() != TestEngine.maxRate)
			testSetup.setMaxRate(TestEngine.maxRate.toString())
	}

	/**
	 * The dedup of the picker keeps the list consistent by the time this runs.
	 */
	fun startTest() {
		if (!testSetup.hasSources()) return
		TestRunState.testSetup = testSetup
		_requests.trySend(Request.StartTest)
	}

	/** The Add source action, where a whole picked hierarchy leaves nothing to select. */
	fun openPicker() {
		if (testSetup.hierarchyPicked()) return
		// a picker return must keep the sources, so declare the origin before navigating
		testSetup.markOpenedPicker()
		_requests.trySend(Request.OpenPicker)
	}

	fun hierarchyPicked(): Boolean = testSetup.hierarchyPicked()
}
