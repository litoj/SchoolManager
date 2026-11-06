package cz.litoj.schlmgr.ui.screen.test

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.PopupMargin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process-wide state of one running test.
 * - it holds the engine, the item models, the typed answers and the grading.
 * - written only through the doors of this object.
 * - a rotation re-attaches to the same run, and a confirmed exit resets it.
 * - the io halves run on the scope of the host screen, so a landing after its death drops.
 * - so a run never outlives the screen that would show it.
 */
object TestRunState {

	/** The screen of the run, which supplies the lifetime and the screen events. */
	interface Host {
		val scope: CoroutineScope

		fun isAlive(): Boolean

		/** The walk found nothing testable, so the screen reports and leaves. */
		fun onNoObjects()

		/** The run ran out of time, so the screen reports before the grading. */
		fun onTimeUp()

		/** The grading landed, so the screen shows the results and leaves. */
		fun onResults(items: List<TestItemModel>, ratio: Float)

		/** The user dropped the run, so the screen leaves with no results. */
		fun onAborted()
	}

	// the timer thread reads it, so the flag must be visible across threads
	@Volatile
	private var test: TestEngine? = null

	private val _items = mutableStateListOf<TestItemModel>()

	/** The items of the run, a snapshot list, so a landing recomposes. */
	val items: List<TestItemModel> = _items

	/** Shared prompts of the run, where only the exact same prompt set merges. */
	var taskGroups = emptyList<TestTaskModel>()
		private set

	private val _answers = mutableStateMapOf<Int, String>()

	/** The typed answers, keyed by item index, which survive scrolling. */
	val answers: Map<Int, String> = _answers

	var secondsLeft by mutableIntStateOf(0)
		private set

	// the timer thread posts through it, so the posts must outlive any one screen.
	// this works like the old runOnUiThread on the activity
	private val mainHandler = Handler(Looper.getMainLooper())

	/** Whether a run is showing, since a fresh screen starts one when not. */
	fun hasRun(): Boolean = test != null

	fun setAnswer(index: Int, text: String) {
		_answers[index] = text
	}

	/** The items a submit would fail for having no answer. */
	fun blankAnswers(): Int = _items.indices.count { _answers[it].isNullOrBlank() }

	/** The living screen, which a rotation replaces while the question stays on the stack. */
	private var host: Host? = null

	private var question: ContinuePopup? = null

	fun attach(host: Host) {
		this.host = host
	}

	/**
	 * Asks before [action] runs, which then gets the screen alive at the answer.
	 * - the end of the run closes the question, since nothing is left to act on.
	 *
	 * @param margin the gap around the card, which sets its width
	 */
	fun confirm(message: String, margin: Dp = PopupMargin, action: (Host) -> Unit) {
		closeQuestion()
		question = ContinuePopup(message, margin) {
			question = null
			host?.let(action)
		}
	}

	private fun closeQuestion() {
		question?.let(Dialogs::dismiss)
		question = null
	}

	/**
	 * Asks before dropping the run ungraded, so no counter moves.
	 * - a run under grading is gone from here, its counters are already moving.
	 */
	fun abort(message: String) {
		if (!hasRun()) return
		confirm(message) { host ->
			reset()
			host.onAborted()
		}
	}

	/** The walk of the run that is starting, null once it lands. */
	@Volatile
	private var walking: Host? = null

	/**
	 * The blocking walk runs off main, and the state lands only into a living screen.
	 * - a rotated screen takes the walk over, since its old host is gone.
	 * - a second start of the same screen inside the window runs nothing.
	 * - an exited screen leaves nothing behind.
	 */
	fun start(host: Host) {
		// the run shows only after the walk lands, so a second call inside the
		// window would otherwise run a second engine beside the first
		if (walking === host) return
		walking = host
		// an empty or below-1 field falls back to the setting of the engine
		val amount = (TestSetupState.amountText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.amount
		val timeSec = (TestSetupState.timeText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.getDefaultTime()
		host.scope.launch(Dispatchers.IO) {
			// the picked sources are container chains, with the root first.
			// - the selected item comes last.
			// - the engine turns a selected word into a direct entry.
			// - it turns a selected chapter into its words
			val src = TestEngine.convertAll(TestSetupState.list)
			if (src.isEmpty()) {
				mainHandler.post {
					if (walking !== host) return@post
					walking = null
					taskGroups = emptyList()
					host.onNoObjects()
				}
				return@launch
			}
			val newTest = TestEngine()
			newTest.setTested(
				amount,
				{ sl ->
					if (test == null) return@setTested false
					mainHandler.post {
						if (sl > 0) secondsLeft = sl
						else {
							host.onTimeUp()
							submit(host)
						}
					}
					true
				},
				timeSec,
				src,
			)
			val models = newTest.getTestSrc().map { TestItemModel(it) }
			val tasks = TestTaskModel.flatten(models, newTest.getTestGroups())
			mainHandler.post {
				if (walking !== host) return@post
				walking = null
				if (!host.isAlive()) return@post
				// a rotation during grading skipped reset, so start from empty
				_items.clear()
				_answers.clear()
				_items.addAll(models)
				taskGroups = tasks
				test = newTest
				newTest.startTest()
			}
		}
	}

	/** The per-item checks and stat re-reads run off the main thread. */
	fun submit(host: Host) {
		closeQuestion()
		val current = test ?: return
		// the run is over for the engine.
		// so a second submit or a late timer tick finds nothing to grade
		test = null
		current.stopTest()
		val typed = _answers.toMap()
		// snapshot on main, since the io grading must not iterate the live list.
		// a back-press exit during grading clears it here
		val items = _items.toList()
		host.scope.launch(Dispatchers.IO) {
			var success = 0
			items.forEachIndexed { index, tim ->
				val text = typed[index]?.trim() ?: ""
				val correct = current.isAnswer(index, text, tim.translations)
				if (correct) success++
				tim.correct = correct
				tim.answer = text
				// the engine wrote the counter updates of the answer itself.
				// so re-read for the results
				tim.refreshStats()
			}
			mainHandler.post {
				if (!host.isAlive()) return@post
				host.onResults(items, success * 100f / items.size)
				reset()
			}
		}
	}

	/** The app left the front, so the countdown holds its remaining time. */
	fun pause() {
		test?.pauseTest()
	}

	/** The app returned, so the countdown continues where it held. */
	fun resume() {
		test?.resumeTest()
	}

	/**
	 * The run is over: stop the countdown outright.
	 * - the timer thread would exit on its next tick, up to a second later.
	 * - until then it still holds a callback of a dead screen.
	 */
	fun reset() {
		closeQuestion()
		host = null
		test?.stopTest()
		test = null
		// a walk still in flight lands into nothing, an exited screen left it behind
		walking = null
		secondsLeft = 0
		_answers.clear()
		_items.clear()
		taskGroups = emptyList()
	}
}
