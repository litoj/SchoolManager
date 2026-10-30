package cz.litoj.schlmgr.ui.screen.test

import androidx.lifecycle.ViewModel
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.testing.TestEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The logic of the test setup screen.
 * - it holds the amount and duration answers, the reverse switch and the fresh-entry decision.
 * - it also holds the two navigation steps, start test and open the source picker.
 * - answers and sources live in [TestSetupState], where this view model is the only writer.
 */
class TestSetupViewModel : ViewModel() {

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
		TestSetupState.setAmount(text)
	}

	fun setTime(text: String) {
		TestSetupState.setTime(text)
	}

	fun removeSource(chain: List<DbItem>) {
		TestSetupState.removeSource(chain)
	}

	/**
	 * The fresh-entry check, run once per entry.
	 * - a return from the picker, the running test or a Prepare test fill keeps the sources.
	 * - only a genuine fresh entry resets the setup.
	 */
	fun onEntered() {
		val (fromPicker, fromSelection) = TestSetupState.consumeEntryMarks()
		if (!fromPicker && !fromSelection) TestSetupState.reset()
	}

	fun restoreAmount() {
		val n = TestSetupState.amountText.toIntOrNull() ?: 0
		if (n < 1) TestSetupState.setAmount(TestEngine.amount.toString())
	}

	fun restoreTime() {
		val n = TestSetupState.timeText.toIntOrNull() ?: 0
		if (n < 1) TestSetupState.setTime(TestEngine.getDefaultTime().toString())
	}

	fun toggleReverse() {
		val on = !TestSetupState.reverseTest
		TestSetupState.setReverse(on)
		TestEngine.applyReversed(on)
		AppSettings.set("reverseTest", on)
	}

	/**
	 * The dedup of the picker keeps the list consistent by the time this runs.
	 */
	fun startTest() {
		if (!TestSetupState.hasSources()) return
		_requests.trySend(Request.StartTest)
	}

	/** The Add source action, where a whole picked hierarchy leaves nothing to select. */
	fun openPicker() {
		if (TestSetupState.hierarchyPicked()) return
		// a picker return must keep the sources, so declare the origin before navigating
		TestSetupState.markOpenedPicker()
		_requests.trySend(Request.OpenPicker)
	}

	fun hierarchyPicked(): Boolean = TestSetupState.hierarchyPicked()
}
