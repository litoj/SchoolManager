package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.WideTextField
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost


/**
 * The running test — a countdown header, the words to answer, and the Done
 * button (submits the answers, shows the results popup). A plain Compose
 * [ComponentActivity] — the surviving test state lives in the companion,
 * so it survives rotation (the double-back-press exit resets it).
 */
class TestActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// the navigation bar stays black under edge-to-edge
		enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
		if (test == null) startTest()
		setContent {
			val snackbar = messageSnackbarHost()
			Box(Modifier.fillMaxSize()) {
				TestScreen()
				// the transient user messages, above the tested words
				SnackbarHost(
					hostState = snackbar,
					// like the shell's host — without the insets the bar rests on the
					// raw window bottom, under the navigation buttons and keyboard
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.navigationBarsPadding()
						.imePadding(),
				)
			}
			DialogHost()
		}
	}

	@Composable
	private fun TestScreen() {
		// Double-back-press to exit. A showing dialog takes the back key itself
		// (its own focused window), so it never reaches this handler.
		BackHandler {
			if (System.currentTimeMillis() - backTime > ExplorerViewModel.EXIT_WINDOW_MS) {
				backTime = System.currentTimeMillis()
				Toast.makeText(applicationContext, R.string.press_exit, Toast.LENGTH_SHORT).show()
			} else reset()
		}
		// imePadding — edge-to-edge never resizes the window for the keyboard.
		// Without it the keyboard overlays the lower words and the Done button
		Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
			TimerHeader()
			// The items to answer, then the Done button as the list's last item:
			// - it sits below the tested words, so it never takes height from them.
			// - one focus handle per answer row, so the keyboard's Next key walks
			//   the chain. A row not yet composed is scrolled to and focused on arrival.
			val answerFocus = remember { HashMap<Int, FocusRequester>() }
			val listState = rememberLazyListState()
			var pendingAnswer by remember { mutableIntStateOf(-1) }
			LazyColumn(Modifier.weight(1f).fillMaxSize(), state = listState) {
				itemsIndexed(list, key = { i, _ -> i }) { index, item ->
					LaunchedEffect(index, pendingAnswer) {
						if (pendingAnswer == index) {
							pendingAnswer = -1
							runCatching { answerFocus[index]?.requestFocus() }
						}
					}
					WordTestRow(item, index, answerFocus, index == list.lastIndex,
						listState, { pendingAnswer = it })
				}
				item {
					// Primary (header-green) background, white text, 5dp rounded corners.
					// Small, centered, and hugging its label — does not span the full width.
					Button(
						onClick = ::onSubmit,
						colors = ButtonDefaults.buttonColors(
							backgroundColor = colorResource(R.color.colorPrimaryHeader),
							contentColor = colorResource(R.color.colorOnHeader),
						),
						shape = RoundedCornerShape(5.dp),
						modifier = Modifier
							.fillMaxWidth()
							.wrapContentWidth(Alignment.CenterHorizontally)
							.padding(5.dp)
							.height(40.dp),
					) {
						Text(stringResource(R.string.done))
					}
				}
			}
		}
	}

	/** The countdown banner — blinks dark red/light gray for the last 30 seconds (as before). */
	@Composable
	private fun TimerHeader() {
		val sl = secondsLeft
		Text(
			stringResource(R.string.time_left, sl),
			Modifier
				.fillMaxWidth()
				.background(colorResource(R.color.colorPrimaryHeader))
				.height(55.dp)
				.padding(top = 5.dp),
			style = TextStyle(
				fontSize = 40.sp,
				color = when {
					// white on the green header — the timer flash takes over at <=30s
					sl > 30 -> colorResource(R.color.colorOnHeader)
					sl % 2 == 0 -> TimerRed
					else -> Color(0xFFDDDDDD)
				},
			),
			textAlign = androidx.compose.ui.text.style.TextAlign.Center,
		)
	}

	@Composable
	private fun WordTestRow(
		item: TestItemModel, index: Int,
		answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
		listState: LazyListState, setPendingAnswer: (Int) -> Unit,
	) {
		Column(Modifier.fillMaxWidth()) {
			// The prompt — the translations by default, the word itself in the reversed
			// direction. Both are parsed like the explorer rows.
			Column(Modifier.fillMaxWidth().padding(5.dp)) {
				val prompt = if (TestEngine.isReversed) ItemRow.nameParser(item.word.name)
				else item.translations.joinToString("\n") { ItemRow.nameParser(it.name) }
				Text(
					prompt,
					style = TextStyle(
						fontSize = 17.sp,
						color = colorResource(R.color.colorPrimaryFg),
					),
				)
				AnswerField(index, R.string.data_translate, answerFocus, isLast,
					listState, setPendingAnswer)
			}
			// Edge-to-edge (outside the content padding) so it reads as a clear
			// line between items, same style as the setup screen's source list.
			// The last row has none, so the list does not end in a line above
			// the Done button.
			if (!isLast) HairlineDivider(Modifier.padding(top = 2.dp))
		}
	}

	/** One answer field — `isLast` hides the keyboard on submit instead of advancing focus. */
	@Composable
	private fun AnswerField(
		index: Int, hintRes: Int,
		answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
		listState: LazyListState, setPendingAnswer: (Int) -> Unit,
	) {
		// same size as the hint text above it
		val focusRequester = remember(index) { answerFocus.getOrPut(index) { FocusRequester() } }
		val keyboard = LocalSoftwareKeyboardController.current
		val scope = rememberCoroutineScope()
		WideTextField(
			value = answers[index] ?: "",
			onValueChange = { answers[index] = it },
			fontSize = 15.sp,
			hint = stringResource(hintRes),
			// The whole field area takes taps and focus. The keyboard action
			// walks to the next answer, and on the last one just hides the
			// keyboard (grading stays on the Done button below).
			focusRequester = focusRequester,
			imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
			onImeAction = {
				if (isLast) keyboard?.hide()
				else {
					// a detached requester (its row left the viewport) throws — the
					// scroll fallback takes over then
					val next = answerFocus[index + 1]
					val landed = next != null && runCatching { next.requestFocus() }.isSuccess
					if (!landed) {
						// the next row is beyond the viewport (or its requester went
						// stale) — scroll to it, the pending index focuses it once
						// composed
						setPendingAnswer(index + 1)
						scope.launch { listState.animateScrollToItem(index + 1) }
					}
				}
			},
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 5.dp),
		)
	}

	/**
	 * Builds and starts the run. The blocking half (the source walk and the
	 * per-word model reads) runs off the main thread — the state lands and
	 * the countdown starts once it does, and only while this activity is
	 * alive, so a rotation or an exit during the walk drops the landing (the
	 * recreated activity walks again, the exited one leaves nothing behind).
	 */
	private fun startTest() {
		// an empty or below-1 field falls back to the engine's setting
		val amount = (TestSetupState.amountText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.amount
		val timeSec = (TestSetupState.timeText.toIntOrNull() ?: 0)
			.takeIf { it >= 1 } ?: TestEngine.getDefaultTime()
		lifecycleScope.launch(Dispatchers.IO) {
			// The picked sources are container chains (root first, the selected
			// item last) — TestEngine.convertAll turns a selected word into a
			// direct entry and a selected chapter into its words.
			val src = TestEngine.convertAll(TestSetupState.list)
			if (src.isEmpty()) {
				runOnUiThread {
					Toast.makeText(applicationContext, R.string.fail_no_objects, Toast.LENGTH_SHORT).show()
					finish()
				}
				return@launch
			}
			val newTest = TestEngine()
			newTest.setTested(
				amount,
				{ sl ->
					if (test == null) return@setTested false
					runOnUiThread {
						if (sl > 0) secondsLeft = sl
						else onSubmit()
					}
					true
				},
				timeSec,
				src,
			)
			val models = newTest.getTestSrc().map { TestItemModel(it) }
			runOnUiThread {
				if (isFinishing || isDestroyed) return@runOnUiThread
				// a rotation during grading skipped reset() — start from empty
				list.clear()
				answers.clear()
				list.addAll(models)
				test = newTest
				newTest.startTest()
			}
		}
	}

	/**
	 * Grades the run and shows the results. The per-item checks and stat
	 * re-reads run off the main thread — the popup follows once they land.
	 */
	private fun onSubmit() {
		val current = test ?: return
		// the run is over for the engine — a second submit or a late timer
		// tick finds nothing to grade
		test = null
		current.stopTest()
		val typed = answers.toMap()
		// snapshot on main — the io grading must not iterate the live list
		// (a back-press exit during grading clears it here)
		val items = list.toList()
		lifecycleScope.launch(Dispatchers.IO) {
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
			runOnUiThread {
				if (isFinishing || isDestroyed) return@runOnUiThread
				TestResultsPopup(items, success * 100f / items.size)
				reset()
			}
		}
	}

	private fun reset() {
		// the run is over — stop the countdown outright instead of letting the
		// thread exit on its next tick (up to a second later, still holding
		// this activity's callback)
		test?.stopTest()
		test = null
		secondsLeft = 0
		answers.clear()
		list.clear()
		// a quick re-entry must get its own first-press hint, not the
		// leftover window of this exit
		backTime = 0
		// The selected sources and the picker's browsing position survive, so
		// another test on the same sources can start right away. A fresh entry
		// into the setup screen still clears them — re-adding dedups against them
		finish()
	}

	companion object {
		private var backTime = 0L

		// the timer thread reads it — the flag must be visible across threads
		@Volatile
		private var test: TestEngine? = null

		// Snapshot state, like answers
		private val list = mutableStateListOf<TestItemModel>()

		/** The typed answers, keyed by item index (survives scrolling). */
		private val answers = mutableStateMapOf<Int, String>()

		private var secondsLeft by mutableIntStateOf(0)
	}
}

private val TimerRed = Color(0xFFDD0000)
