package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
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

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.components.BareTextField
import cz.litoj.schlmgr.ui.fragments.TestFragment
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.TestResultsPopup


/**
 * The running test: a countdown header, the list of items to answer (words) and
 * the Done button that submits the answers and shows the results popup.
 *
 * A plain Compose [ComponentActivity] — the surviving test state (the [TestEngine], its
 * items and the typed answers) lives in the companion, so it survives rotation and
 * the double-back-press exit the same way the old statics did.
 */
class TestActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		showUserMessages()
		if (test == null) startTest()
		setContent {
			DialogHost()
			TestScreen()
		}
	}

	@Composable
	private fun TestScreen() {
		// Double-back-press to exit. A showing dialog takes the back key itself
		// (its own focused window), so it never reaches this handler.
		BackHandler {
			if (System.currentTimeMillis() - backTime > 3_000) {
				backTime = System.currentTimeMillis()
				Toast.makeText(applicationContext, R.string.press_exit, Toast.LENGTH_SHORT).show()
			} else reset()
		}
		Column(Modifier.fillMaxSize().systemBarsPadding()) {
			TimerHeader()
			// The items to answer, then the Done button as the list's last item — it
			// sits below the tested words and is reached by scrolling, so it never
			// takes height from them. One focus handle per answer row, so the
			// keyboard's Next key can walk the chain (CreatorPopup uses the same
			// pattern for its translation rows).
			val answerFocus = remember { HashMap<Int, FocusRequester>() }
			LazyColumn(Modifier.weight(1f).fillMaxSize()) {
				itemsIndexed(list) { index, item ->
					WordTestRow(item, index, answerFocus, index == list.lastIndex)
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

	/** The countdown banner; blinks red/white for the last 30 seconds (as before). */
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
					// normal: white on the green header; <=30s: the timer flash
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
	) {
		Column(Modifier.fillMaxWidth()) {
			// The prompt: the translations by default, the word itself in the reversed
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
				AnswerField(index, R.string.data_translate, answerFocus, isLast)
			}
			// Edge-to-edge (outside the content padding) so it reads as a clear
			// line between items; same style as the TestFragment source list.
			// The last row has none, so the list does not end in a line above
			// the Done button.
			if (!isLast) Spacer(
				Modifier
					.fillMaxWidth()
					.padding(top = 2.dp)
					.height(0.6.dp)
					.background(colorResource(R.color.colorSourceDivider))
			)
		}
	}

	@Composable
	private fun AnswerField(
		index: Int, hintRes: Int,
		answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
	) {
		// Same size as the hint text above it (the old rows used 15sp).
		val focusRequester = remember(index) { answerFocus.getOrPut(index) { FocusRequester() } }
		val keyboard = LocalSoftwareKeyboardController.current
		BareTextField(
			value = answers[index] ?: "",
			onValueChange = { answers[index] = it },
			fontSize = 15.sp,
			hint = stringResource(hintRes),
			// Full width and a touch-sized height, so tapping anywhere on the field area
			// focuses it; the keyboard action walks to the next answer, and on the last
			// one just hides the keyboard (grading stays on the Done button below).
			fillWidth = true,
			focusRequester = focusRequester,
			imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
			onImeAction = {
				if (isLast) keyboard?.hide()
				else answerFocus[index + 1]?.requestFocus()
			},
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 5.dp),
		)
	}

	private fun startTest() {
		val newTest = TestEngine()
		// The picked sources are container chains (root first, the selected item last)
		// — TestEngine.convertAll turns a selected word into a direct entry and a
		// selected chapter into its words.
		val src = TestEngine.convertAll(TestFragment.list)
		if (src.isEmpty()) {
			Toast.makeText(applicationContext, R.string.fail_no_objects, Toast.LENGTH_SHORT).show()
			finish()
			return
		}
		newTest.setTested(
			TestFragment.amountText.toIntOrNull() ?: TestEngine.amount,
			{ sl ->
				if (test == null) return@setTested false
				runOnUiThread {
					if (sl > 0) secondsLeft = sl
					else onSubmit()
				}
				true
			},
			TestFragment.timeText.toIntOrNull() ?: TestEngine.getDefaultTime(),
			src,
		)
		for (entry in newTest.getTestSrc()) list.add(TestItemModel(entry))
		test = newTest
		newTest.startTest()
	}

	private fun onSubmit() {
		val current = test ?: return
		var success = 0
		list.forEachIndexed { index, tim ->
			val text = answers[index] ?: ""
			val correct = current.isAnswer(index, text)
			if (correct) success++
			tim.correct = correct
			tim.answer = text
			// the engine wrote the answer's sf updates itself — re-read for the results
			tim.refreshSf()
		}
		TestResultsPopup(list, success * 100f / list.size)
		reset()
	}

	private fun reset() {
		// the run is over: stop the countdown outright instead of letting the
		// thread exit on its next tick (up to a second later, still holding
		// this activity's callback)
		test?.stopTest()
		test = null
		secondsLeft = 0
		answers.clear()
		list.clear()
		// The selected sources (TestFragment.list) and the picker's backLog survive:
		// finishing or leaving a test keeps them, so another test on the same sources
		// can start right away. They are still cleared on a fresh entry into the setup
		// tab (TestFragment.onCreateView), and re-adding from the picker dedups against
		// the kept entries.
		finish()
	}

	companion object {
		private var backTime = 0L

		private var test: TestEngine? = null
		private val list = ArrayList<TestItemModel>()

		/** The typed answers, keyed by item index (survives scrolling; was the View cache). */
		private val answers = mutableStateMapOf<Int, String>()

		private var secondsLeft by mutableIntStateOf(0)
	}
}

private val TimerRed = Color(0xFFDD0000)
