package cz.litoj.schlmgr.ui.screen.test

import androidx.lifecycle.ViewModel
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.testing.TestEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The test setup screen's logic — amount/duration answers, the reverse
 * switch, the fresh-entry decision and the two navigation steps (start
 * test, open the source picker).
	 * - answers and sources live in [TestSetupState]. This view model is the screen's only writer to them.
 */
class TestSetupViewModel : ViewModel() {

	/**
	 * One step only the view layer can perform for this screen — starting the
	 * running-test activity or navigating to the source picker.
	 */
	sealed interface Request {
		/** The Start button — the sources are non-empty (see [startTest]). */
		data object StartTest : Request

		/** 'Add source' — open the picker for more sources. */
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
	 * Fresh-entry check, once per entry — returns from the picker, the
	 * running test or a 'Prepare test' fill keep the sources. Only a
	 * genuine fresh entry resets the setup.
	 */
	fun onEntered() {
		val (fromPicker, fromSelection) = TestSetupState.consumeEntryMarks()
		if (!fromPicker && !fromSelection) TestSetupState.reset()
	}

	/** Restores an unusable amount field to the engine's setting on focus loss. */
	fun restoreAmount() {
		val n = TestSetupState.amountText.toIntOrNull() ?: 0
		if (n < 1) TestSetupState.setAmount(TestEngine.amount.toString())
	}

	/** Restores an unusable duration field to the engine's default on focus loss. */
	fun restoreTime() {
		val n = TestSetupState.timeText.toIntOrNull() ?: 0
		if (n < 1) TestSetupState.setTime(TestEngine.getDefaultTime().toString())
	}

	/** Flips the reverse-test direction and persists it at once. */
	fun toggleReverse() {
		val on = !TestSetupState.reverseTest
		TestSetupState.setReverse(on)
		TestEngine.isReversed = on
		AppSettings.set("reverseTest", on)
	}

	/**
	 * The Start button — does nothing without sources (the picker's dedup keeps
	 * the list consistent by the time this runs).
	 */
	fun startTest() {
		if (!TestSetupState.hasSources()) return
		_requests.trySend(Request.StartTest)
	}

	/** 'Add source': the whole hierarchy can't be added twice — nothing to select then. */
	fun openPicker() {
		if (TestSetupState.hierarchyPicked()) return
		// a picker return must keep the sources — declare the origin before navigating
		TestSetupState.markOpenedPicker()
		_requests.trySend(Request.OpenPicker)
	}

	fun hierarchyPicked(): Boolean = TestSetupState.hierarchyPicked()
}
