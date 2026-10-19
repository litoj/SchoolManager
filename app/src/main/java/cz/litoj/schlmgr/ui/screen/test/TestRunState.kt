package cz.litoj.schlmgr.ui.screen.test

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process-wide state of one running test — the engine, the item models,
 * the typed answers and the grading.
 * - written only through this object's doors — a rotation re-attaches to the same run; the double-back exit resets it.
 * - the io halves (the source walk, the grading) run on the host screen's scope; a landing after the host's death drops, so a run never outlives the screen that would show it.
 */
object TestRunState {

	/** The run's screen — supplies the lifetime and the screen-owned events. */
	interface Host {
		val scope: CoroutineScope

		fun isAlive(): Boolean

		/** The walk found nothing testable — the screen reports and leaves. */
		fun onNoObjects()

		/** The grading landed — the screen shows the results and leaves. */
		fun onResults(items: List<TestItemModel>, ratio: Float)
	}

	// the timer thread reads it — the flag must be visible across threads
	@Volatile
	private var test: TestEngine? = null

	private val _items = mutableStateListOf<TestItemModel>()

	/** The run's items — a snapshot list, so the run's landing recomposes. */
	val items: List<TestItemModel> = _items

	private val _answers = mutableStateMapOf<Int, String>()

	/** The typed answers, keyed by item index (survives scrolling). */
	val answers: Map<Int, String> = _answers

	var secondsLeft by mutableIntStateOf(0)
		private set

	private var backTime = 0L

	// the timer thread posts through it — the posts must outlive any one
	// screen, like the old runOnUiThread on the activity
	private val mainHandler = Handler(Looper.getMainLooper())

	/** Whether a run is showing — a fresh screen starts one when not. */
	fun hasRun(): Boolean = test != null

	/** One typed answer — the answer fields' write door. */
	fun setAnswer(index: Int, text: String) {
		_answers[index] = text
	}

	/**
	 * One back press of the double-press window.
	 * @return true for the first press (the hint shows), false for the second (the exit).
	 */
	fun pressExit(): Boolean =
		if (System.currentTimeMillis() - backTime > ExplorerViewModel.EXIT_WINDOW_MS) {
			backTime = System.currentTimeMillis()
			true
		} else false

	/**
	 * Builds and starts the run. The blocking half (the source walk and the
	 * per-word model reads) runs off the main thread — the state lands and
	 * the countdown starts once it does, and only into a living screen (a
	 * rotated screen walks again, an exited one leaves nothing behind).
	 */
	fun start(host: Host) {
		// an empty or below-1 field falls back to the engine's setting
		val amount = (TestSetupState.amountText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.amount
		val timeSec = (TestSetupState.timeText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.getDefaultTime()
		host.scope.launch(Dispatchers.IO) {
			// The picked sources are container chains (root first, the selected
			// item last) — TestEngine.convertAll turns a selected word into a
			// direct entry and a selected chapter into its words.
			val src = TestEngine.convertAll(TestSetupState.list)
			if (src.isEmpty()) {
				mainHandler.post { host.onNoObjects() }
				return@launch
			}
			val newTest = TestEngine()
			newTest.setTested(
				amount,
				{ sl ->
					if (test == null) return@setTested false
					mainHandler.post {
						if (sl > 0) secondsLeft = sl
						else submit(host)
					}
					true
				},
				timeSec,
				src,
			)
			val models = newTest.getTestSrc().map { TestItemModel(it) }
			mainHandler.post {
				if (!host.isAlive()) return@post
				// a rotation during grading skipped reset() — start from empty
				_items.clear()
				_answers.clear()
				_items.addAll(models)
				test = newTest
				newTest.startTest()
			}
		}
	}

	/**
	 * Grades the run and hands the results to [Host.onResults]. The per-item
	 * checks and stat re-reads run off the main thread — the results follow
	 * once they land.
	 */
	fun submit(host: Host) {
		val current = test ?: return
		// the run is over for the engine — a second submit or a late timer
		// tick finds nothing to grade
		test = null
		current.stopTest()
		val typed = _answers.toMap()
		// snapshot on main — the io grading must not iterate the live list
		// (a back-press exit during grading clears it here)
		val items = _items.toList()
		host.scope.launch(Dispatchers.IO) {
			var success = 0
			items.forEachIndexed { index, tim ->
				val text = typed[index] ?: ""
				val correct = current.isAnswer(index, text, tim.translations)
				if (correct) success++
				tim.correct = correct
				tim.answer = text
				// the engine wrote the answer's counter updates itself — re-read for the results
				tim.refreshStats()
			}
			mainHandler.post {
				if (!host.isAlive()) return@post
				host.onResults(items, success * 100f / items.size)
				reset()
			}
		}
	}

	/**
	 * The run is over — stop the countdown outright instead of letting the
	 * thread exit on its next tick (up to a second later, still holding a
	 * dead screen's callback).
	 */
	fun reset() {
		test?.stopTest()
		test = null
		secondsLeft = 0
		_answers.clear()
		_items.clear()
		// a quick re-entry must get its own first-press hint, not the
		// leftover window of this exit
		backTime = 0
	}
}
