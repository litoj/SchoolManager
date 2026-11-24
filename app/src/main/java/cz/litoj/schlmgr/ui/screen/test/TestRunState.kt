package cz.litoj.schlmgr.ui.screen.test

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import cz.litoj.schlmgr.data.CounterService
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestRunConfig
import cz.litoj.schlmgr.testing.TestRunData
import cz.litoj.schlmgr.testing.TestRunner
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.component.NumberInput
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
		val graphs: GraphQueries
		val counters: CounterService

		fun isAlive(): Boolean

		/** A living host may still be paused, which holds the countdown. */
		fun isForeground(): Boolean = isAlive()

		/** The walk found nothing testable, so the screen reports and leaves. */
		fun onNoObjects()

		/** The run ran out of time, so the screen reports before the grading. */
		fun onTimeUp()

		/** The grading landed, so the screen shows the results and leaves. */
		fun onResults(items: List<TestItemModel>, ratio: Float)

		/** The user dropped the run, so the screen leaves with no results. */
		fun onAborted()
	}

	private class ActiveRun(
		val engine: TestEngine,
		val graphs: GraphQueries,
		val counters: CounterService,
	)

	// the timer thread reads it, so the run must be visible across threads
	@Volatile
	private var run: ActiveRun? = null

	var testConfig = TestRunConfig.current()

	private val _items = mutableStateListOf<TestItemModel>()

	var testSetup = TestSetupState.shared

	/** The items of the run, a snapshot list, so a landing recomposes. */
	val items: List<TestItemModel> = _items

	/** Shared prompts of the run, where only the exact same prompt set merges. */
	var taskGroups by mutableStateOf(emptyList<TestTaskModel>())
		private set

	/** Total flattened answer fields, computed once per run. */
	var answerCount by mutableIntStateOf(0)
		private set

	private val _answers = mutableStateMapOf<Int, String>()

	/** The typed answers, keyed by flattened answer-field index, which survive scrolling. */
	val answers: Map<Int, String> = _answers

	var secondsLeft by mutableIntStateOf(0)
		private set

	// the timer thread posts through it, so the posts must outlive any one screen.
	// this works like the old runOnUiThread on the activity
	private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

	/** Whether a run is showing, since a fresh screen starts one when not. */
	fun hasRun(): Boolean = run != null

	fun setAnswer(index: Int, text: String) {
		_answers[index] = text
	}

	/** The answer fields a submit would fail for having no answer. */
	fun blankAnswers(): Int = blankAnswerCount(_answers, answerCount)

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
		// an empty field falls back to the setting of the engine
		val amount = (testSetup.amountText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.amount
		val perChar = NumberInput.amount(testSetup.timeText) ?: TestEngine.getSecondsPerChar()
		val graphs = host.graphs
		val counters = host.counters
		host.scope.launch(Dispatchers.IO) {
			// the picked sources are container chains, with the root first.
			// - the selected item comes last.
			// - the engine turns a selected word into a direct entry.
			// - it turns a selected chapter into its words
			val config = TestRunConfig.current()
			val runData = TestRunData.read(testSetup.sourceSnapshot(), graphs)
			val src = runData.entries
			if (src.isEmpty()) {
				mainHandler.post {
					if (walking !== host) return@post
					walking = null
					taskGroups = emptyList()
					answerCount = 0
					host.onNoObjects()
				}
				return@launch
			}
			val newTest = TestEngine(runData::parentsOf)
			newTest.setTested(
				amount,
				{ sl ->
					if (run?.engine !== newTest) return@setTested false
					mainHandler.post {
						if (run?.engine !== newTest) return@post
						if (sl > 0) secondsLeft = sl
						else {
							// a rotation replaced the host that started the countdown
							val live = this@TestRunState.host ?: return@post
							live.onTimeUp()
							submit(live)
						}
					}
					true
				},
				perChar,
				src,
				config,
			)
			val models = newTest.getTestSrc().map { TestItemModel(it) }
			val tasks = TestTaskModel.flatten(models, newTest.getTestGroups(), config)
			mainHandler.post {
				if (walking !== host) return@post
				walking = null
				if (!host.isAlive()) return@post
				// a rotation during grading skipped reset, so start from empty
				_items.clear()
				_answers.clear()
				_items.addAll(models)
				taskGroups = tasks
				answerCount = tasks.sumOf { it.answerFields.size }
				run = ActiveRun(newTest, graphs, counters)
				testConfig = config
				newTest.startTest()
				if (!host.isForeground()) newTest.pauseTest()
			}
		}
	}

	/** The per-item checks and stat re-reads run off the main thread. */
	fun submit(host: Host) {
		closeQuestion()
		val current = run ?: return
		// the run is over for the engine.
		// so a second submit or a late timer tick finds nothing to grade
		run = null
		current.engine.stopTest()
		val typed = _answers.toMap()
		// snapshot on main, since the io grading must not iterate the live list.
		// a back-press exit during grading clears it here
		val items = _items.toList()
		val tasks = taskGroups.toList()
		host.scope.launch(Dispatchers.IO) {
			val runner = TestRunner(current.engine, current.counters)
			val answersByOwner = mutableMapOf<TestItemModel, MutableList<String>>()
			val typedByOwner = mutableMapOf<TestItemModel, MutableList<String>>()
			for (task in tasks) {
				val raw = task.answerFields.mapIndexed { slot, _ ->
					typed[task.startIndex + slot].orEmpty()
				}
				val assigned = task.assign(raw)
				for ((slot, field) in task.answerFields.withIndex()) {
					typedByOwner.getOrPut(field.owner) { mutableListOf() } += raw[slot]
				}
				for ((entry, texts) in assigned) {
					answersByOwner.getOrPut(entry) { mutableListOf() }.addAll(texts)
				}
			}
			var success = 0
			items.forEachIndexed { index, tim ->
				val answers = answersByOwner[tim].orEmpty()
				val correct = runner.answer(index, answers, tim.translations)
				if (correct) success++
				tim.correct = correct
				// the results show what was typed, not the grading assignment
				tim.answers = typedByOwner[tim].orEmpty()
				// the runner wrote the counter updates of the answer itself.
				// so re-read for the results
				current.graphs.get(tim.entry.word.id)?.let(tim::refreshStats)
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
		run?.engine?.pauseTest()
	}

	/** The app returned, so the countdown continues where it held. */
	fun resume() {
		run?.engine?.resumeTest()
	}

	/**
	 * The run is over: stop the countdown outright.
	 * - the timer thread would exit on its next tick, up to a second later.
	 * - until then it still holds a callback of a dead screen.
	 */
	fun reset() {
		closeQuestion()
		host = null
		run?.engine?.stopTest()
		run = null
		// a walk still in flight lands into nothing, an exited screen left it behind
		walking = null
		secondsLeft = 0
		_answers.clear()
		_items.clear()
		taskGroups = emptyList()
		answerCount = 0
		testConfig = TestRunConfig.current()
	}
}

internal fun blankAnswerCount(answers: Map<Int, String>, answerCount: Int): Int =
	(0 until answerCount).count { answers[it].isNullOrBlank() }
