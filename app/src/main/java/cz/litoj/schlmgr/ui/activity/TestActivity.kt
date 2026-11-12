package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.AnswerField
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.PromptLines
import cz.litoj.schlmgr.ui.component.SharedPromptRow
import cz.litoj.schlmgr.ui.component.promptItems
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.shell.MessageSnackbarHost
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost


/**
 * - a countdown header, the words to answer and the Done button.
 * - a plain Compose [ComponentActivity] rendering [TestRunState].
 * - the run state is process-wide, so a rotation re-attaches and a confirmed exit resets.
 */
class TestActivity : ComponentActivity(), TestRunState.Host {

	override val scope get() = lifecycleScope

	override fun isAlive(): Boolean = !isFinishing && !isDestroyed

	override fun onNoObjects() {
		Toast.makeText(applicationContext, R.string.fail_no_objects, Toast.LENGTH_SHORT).show()
		finish()
	}

	override fun onTimeUp() {
		Toast.makeText(applicationContext, R.string.time_up, Toast.LENGTH_SHORT).show()
	}

	override fun onResults(items: List<TestItemModel>, ratio: Float) {
		TestResultsPopup(items, ratio)
		finish()
	}

	override fun onAborted() = finish()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// the navigation bar stays black under edge-to-edge
		enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
		// a run away from the front holds its countdown, since the user never left on purpose
		lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onPause(owner: LifecycleOwner) {
				TestRunState.pause()
			}

			override fun onResume(owner: LifecycleOwner) {
				TestRunState.resume()
			}
		})
		TestRunState.attach(this)
		if (!TestRunState.hasRun()) TestRunState.start(this)
		setContent {
			val snackbar = messageSnackbarHost()
			Box(Modifier.fillMaxSize()) {
				TestScreen()
				// the transient user messages, above the tested words
				MessageSnackbarHost(
					hostState = snackbar,
					// without the insets the bar rests on the raw window bottom,
					// under the navigation buttons
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
		// the dialog takes the back key in its own focused window.
		// so the confirm never sees a second press
		val abortQuestion = stringResource(R.string.test_abort_question)
		BackHandler { TestRunState.abort(abortQuestion) }
		// edge-to-edge never resizes the window for the keyboard.
		// imePadding keeps the lower words and the Done button clear of it.
		Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
			TimerHeader()
			// the Done button closes the list, so it never takes height from the words.
			// - one focus handle per answer row walks the keyboard chain.
			// - a row not yet composed is scrolled to and focused once it composes.
			val answerFocus = remember { HashMap<Int, FocusRequester>() }
			val listState = rememberLazyListState()
			var pendingAnswer by remember { mutableIntStateOf(-1) }
			LazyColumn(Modifier.weight(1f).fillMaxSize(), state = listState) {
				itemsIndexed(TestRunState.taskGroups, key = { i, _ -> i }) { index, task ->
					if (task.answerFields.size > 1) {
						SharedPromptRow(task, pendingAnswer, answerFocus, task.startIndex + task.members.lastIndex == TestRunState.items.lastIndex,
							listState, { pendingAnswer = it })
					} else {
						val item = task.members.single()
						LaunchedEffect(task.startIndex, pendingAnswer) {
							if (pendingAnswer == task.startIndex) {
								pendingAnswer = -1
								runCatching { answerFocus[task.startIndex]?.requestFocus() }
							}
						}
						WordTestRow(item, task.startIndex, answerFocus, task.startIndex == TestRunState.items.lastIndex,
							listState, { pendingAnswer = it })
					}
				}
				item {
					// the app header colours mark the one commit action of the screen.
					Button(
						onClick = ::onDone,
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

	private fun onDone() {
		val blank = TestRunState.blankAnswers()
		if (blank == 0) TestRunState.submit(this)
		else TestRunState.confirm(
			resources.getQuantityString(R.plurals.test_blank_answers, blank, blank),
			20.dp,
			TestRunState::submit,
		)
	}

	/**
	 * The countdown banner with the answered count below.
	 * It blinks dark red and light gray for the last 30 seconds.
	 * - the abort button overlays it, so the countdown stays centered.
	 */
	@Composable
	private fun TimerHeader() {
		val sl = TestRunState.secondsLeft
		Box(
			Modifier
				.fillMaxWidth()
				.background(colorResource(R.color.colorPrimaryHeader)),
		) {
			Column(
				Modifier
					.fillMaxWidth()
					.padding(bottom = 3.dp),
			) {
				Text(
					stringResource(R.string.time_left, sl),
					Modifier
						.fillMaxWidth()
						.height(50.dp),
					style = TextStyle(
						fontSize = 40.sp,
						color = when {
							// white on the green header until the timer flash takes over at 30s or less
							sl > 30 -> colorResource(R.color.colorOnHeader)
							sl % 2 == 0 -> TimerRed
							else -> Color(0xFFDDDDDD)
						},
					),
					textAlign = androidx.compose.ui.text.style.TextAlign.Center,
				)
				// the header states the progress, since the countdown alone hides it
				val answered = TestRunState.answers.count { it.value.isNotBlank() }
				Text(
					pluralStringResource(R.plurals.test_answered, answered, answered, TestRunState.items.size),
					Modifier.fillMaxWidth(),
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorOnHeader)),
					textAlign = androidx.compose.ui.text.style.TextAlign.Center,
				)
			}
			val question = stringResource(R.string.test_abort_question)
			// red on the green header so the exit is noticed
			Button(
				onClick = { TestRunState.abort(question) },
				colors = ButtonDefaults.buttonColors(
					backgroundColor = TimerRed,
					contentColor = Color.White,
				),
				shape = RoundedCornerShape(5.dp),
				modifier = Modifier
					.align(Alignment.CenterStart)
					.padding(start = 5.dp)
					.height(40.dp),
			) {
				Text(stringResource(R.string.test_abort))
			}
		}
	}

	@Composable
	private fun WordTestRow(
		item: TestItemModel, index: Int,
		answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
		listState: LazyListState, setPendingAnswer: (Int) -> Unit,
	) {
		Column(Modifier.fillMaxWidth()) {
			Column(Modifier.fillMaxWidth().padding(5.dp)) {
				PromptLines(promptItems(item.word, item.translations))
				// the asked side names itself: the word in the default direction, a translation reversed
				AnswerField(
					index,
					when {
						TestEngine.isReversed && TestEngine.requireAllVariants -> R.string.test_variants_translation
						TestEngine.isReversed -> R.string.data_translate
						TestEngine.requireAllVariants -> R.string.test_variants_word
						else -> R.string.data_word
					},
					answerFocus, isLast,
					listState, setPendingAnswer,
				)
			}
			// the line sits outside the content padding, so it reads clearly between items.
			// - the style matches the source list of the setup screen.
			// - the last row has none, so no line sits above the Done button.
			if (!isLast) HairlineDivider(Modifier.padding(top = 2.dp))
		}
	}
}

private val TimerRed = Color(0xFFDD0000)
