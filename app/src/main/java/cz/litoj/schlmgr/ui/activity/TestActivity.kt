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
import kotlinx.coroutines.launch

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.WideTextField
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost


/**
 * - a countdown header, the words to answer and the Done button.
 * - a plain Compose [ComponentActivity] rendering [TestRunState].
 * - the run state is process-wide, so a rotation re-attaches and the double-back exit resets.
 */
class TestActivity : ComponentActivity(), TestRunState.Host {

	override val scope get() = lifecycleScope

	override fun isAlive(): Boolean = !isFinishing && !isDestroyed

	override fun onNoObjects() {
		Toast.makeText(applicationContext, R.string.fail_no_objects, Toast.LENGTH_SHORT).show()
		finish()
	}

	override fun onResults(items: List<TestItemModel>, ratio: Float) {
		TestResultsPopup(items, ratio)
		finish()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// the navigation bar stays black under edge-to-edge
		enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK))
		if (!TestRunState.hasRun()) TestRunState.start(this)
		setContent {
			val snackbar = messageSnackbarHost()
			Box(Modifier.fillMaxSize()) {
				TestScreen()
				// the transient user messages, above the tested words
				SnackbarHost(
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
		// a showing dialog takes the back key in its own focused window.
		// so the double-press exit never sees it.
		BackHandler {
			if (TestRunState.pressExit()) {
				Toast.makeText(applicationContext, R.string.press_exit, Toast.LENGTH_SHORT).show()
			} else {
				TestRunState.reset()
				finish()
			}
		}
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
				itemsIndexed(TestRunState.items, key = { i, _ -> i }) { index, item ->
					LaunchedEffect(index, pendingAnswer) {
						if (pendingAnswer == index) {
							pendingAnswer = -1
							runCatching { answerFocus[index]?.requestFocus() }
						}
					}
					WordTestRow(item, index, answerFocus, index == TestRunState.items.lastIndex,
						listState, { pendingAnswer = it })
				}
				item {
					// the app header colours mark the one commit action of the screen.
					Button(
						onClick = { TestRunState.submit(this@TestActivity) },
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

	/** The countdown banner. It blinks dark red and light gray for the last 30 seconds. */
	@Composable
	private fun TimerHeader() {
		val sl = TestRunState.secondsLeft
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
					// white on the green header until the timer flash takes over at 30s or less
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
			// the translations prompt by default, the word itself in the reversed direction.
			// both are parsed like the explorer rows.
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
			// the line sits outside the content padding, so it reads clearly between items.
			// - the style matches the source list of the setup screen.
			// - the last row has none, so no line sits above the Done button.
			if (!isLast) HairlineDivider(Modifier.padding(top = 2.dp))
		}
	}

	/** One answer field. [isLast] hides the keyboard on submit instead of advancing focus. */
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
			value = TestRunState.answers[index] ?: "",
			onValueChange = { TestRunState.setAnswer(index, it) },
			fontSize = 15.sp,
			hint = stringResource(hintRes),
			// the whole field area takes taps and focus.
			// - the keyboard action walks to the next answer.
			// - the last answer only hides the keyboard, since grading stays on Done.
			focusRequester = focusRequester,
			imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
			onImeAction = {
				if (isLast) keyboard?.hide()
				else {
					// a requester whose row left the viewport throws, so the scroll fallback runs
					val next = answerFocus[index + 1]
					val landed = next != null && runCatching { next.requestFocus() }.isSuccess
					if (!landed) {
						// the next row sits beyond the viewport or lost its requester.
						// scroll to it, then the pending index focuses it once composed.
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
}

private val TimerRed = Color(0xFFDD0000)
