package com.schlmgr.gui.activity

import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.schlmgr.R
import com.schlmgr.gui.Controller
import com.schlmgr.gui.components.BareTextField
import com.schlmgr.gui.fragments.TestFragment
import com.schlmgr.gui.list.HierarchyItemModel
import com.schlmgr.gui.list.TestItemModel
import com.schlmgr.gui.popup.AbstractPopup
import com.schlmgr.gui.popup.FullPicture
import com.schlmgr.gui.popup.TestResultsPopup

import com.schlmgr.gui.engine.objects.MainChapter
import com.schlmgr.gui.engine.objects.Picture
import com.schlmgr.gui.engine.objects.Word
import com.schlmgr.gui.engine.objects.templates.Container
import com.schlmgr.gui.engine.testing.Test
import com.schlmgr.gui.engine.testing.Test.SrcPath


/**
 * The running test: a countdown header, the list of items to answer (words or pictures)
 * and the Done button that submits the answers and shows the results popup.
 *
 * A plain Compose [ComponentActivity] — the surviving engine state (the [Test], its
 * items and the typed answers) lives in the companion, so it survives rotation and
 * the double-back-press exit the same way the old statics did.
 */
class TestActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		Controller.currentActivity = this
		if (test == null) startTest()
		setContent { TestScreen() }
	}

	override fun onResume() {
		super.onResume()
		Controller.currentActivity = this
	}

	override fun onPostResume() {
		super.onPostResume()
		// Restore popups (e.g. FullPicture) shown before a rotation, once — the job
		// PopupCareActivity did for the activities still using it.
		val repaint = Controller.getControl()?.popupRepaint ?: return
		if (repaint.isNotEmpty() && !resumed) {
			resumed = true
			for (r in repaint) Thread(r, "popupRepaint").start()
		}
	}

	override fun onDestroy() {
		// The oldDestroy split from PopupCareActivity: when the test was already reset
		// (i.e. the results popup is showing), leave the popup alone — it outlives this
		// activity on the next one in the back stack.
		if (test != null) {
			if (AbstractPopup.isActive && !AbstractPopup.isShowing) AbstractPopup.clear()
			else runOnUiThread(AbstractPopup::clean)
		}
		super.onDestroy()
	}

	@Composable
	private fun TestScreen() {
		// Double-back-press to exit, after the active-popup check (as before).
		BackHandler {
			if (AbstractPopup.isActive) {
				AbstractPopup.clear()
				return@BackHandler
			}
			if (System.currentTimeMillis() - backTime > 3_000) {
				backTime = System.currentTimeMillis()
				Toast.makeText(applicationContext, R.string.press_exit, Toast.LENGTH_SHORT).show()
			} else reset()
		}
		Column(Modifier.fillMaxSize().systemBarsPadding()) {
			TimerHeader()
			// The items to answer, taking all remaining space.
			// One focus handle per answer row, so the keyboard's Next key can walk the
			// chain (CreatorPopup uses the same pattern for its translation rows).
			val answerFocus = remember { HashMap<Int, FocusRequester>() }
			LazyColumn(Modifier.weight(1f).fillMaxSize()) {
				itemsIndexed(list) { index, item ->
					val isLast = index == list.lastIndex
					if (TestFragment.picTest) PictureTestRow(item, index, answerFocus, isLast)
					else WordTestRow(item, index, answerFocus, isLast)
				}
			}
			// The Done button at the very end of the screen, below the list — it can never
			// overlap the tested items. Primary (header-green) background, white text,
			// 5dp rounded corners. Small, centered, and hugging its label — does not span
			// the full width.
			Button(
				onClick = ::onSubmit,
				colors = ButtonDefaults.buttonColors(
					backgroundColor = colorResource(R.color.colorPrimaryHeader),
					contentColor = Color.White,
				),
				shape = RoundedCornerShape(5.dp),
				modifier = Modifier
					.align(Alignment.CenterHorizontally)
					.padding(horizontal = 5.dp)
					.height(40.dp),
			) {
				Text(stringResource(R.string.done))
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
					sl > 30 -> Color.White
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
		Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
			// The hints: newline-joined translations (parsed like the old ListView row).
			val trls = item.children.joinToString("\n") {
				HierarchyItemModel.nameParser(it.name)
			}
			Text(
				trls,
				style = TextStyle(
					fontSize = 17.sp,
					color = colorResource(R.color.colorPrimaryFg),
				),
			)
			AnswerField(index, R.string.data_translate, answerFocus, isLast)
		}
	}

	@Composable
	private fun PictureTestRow(
		item: TestItemModel, index: Int,
		answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
	) {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth()) {
				val iim = item.iim!!
				AsyncPicture(iim, first = true, Modifier.weight(1f))
				val pic2 = iim.pic2
				if (pic2 != null) AsyncPicture(iim, first = false, Modifier.weight(1f))
			}
			AnswerField(index, R.string.data_name, answerFocus, isLast)
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

	/** Renders a bitmap of [com.schlmgr.gui.list.ImageItemModel] once it is decoded. */
	@Composable
	private fun AsyncPicture(
		iim: com.schlmgr.gui.list.ImageItemModel,
		first: Boolean,
		modifier: Modifier,
	) {
		val pic = if (first) iim.pic1 else iim.pic2
		// The bitmaps are set by ImageItemModel's background decoder thread; poll until
		// the one for this slot arrives, then recompose once with the result.
		var bitmap by remember(iim, first) {
			mutableStateOf(if (first) iim.bm1 else iim.bm2)
		}
		LaunchedEffect(iim, first) {
			while (bitmap == null) {
				delay(100.milliseconds)
				bitmap = if (first) iim.bm1 else iim.bm2
			}
		}
		Box(modifier.height(50.dp).padding(5.dp)) {
			bitmap?.let { bm ->
				Image(
					bitmap = bm.asImageBitmap(),
					contentDescription = pic?.toString(),
					contentScale = ContentScale.Crop,
					modifier = Modifier
						.fillMaxSize()
						.clickable { pic?.let { FullPicture(it) } },
				)
			}
		}
	}

	private fun startTest() {
		val newTest: Test<*> = if (TestFragment.picTest) Test(Picture::class.java) else Test(Word::class.java)
		val src = ArrayList<List<Container>>(TestFragment.list.size)
		for (sim in TestFragment.list) {
			val path = ArrayList(sim.path)
			path.add(sim.bd as Container)
			src.add(path)
		}
		val paths: List<SrcPath> = newTest.convertAll(src)
		if (paths.isEmpty()) {
			Toast.makeText(applicationContext, R.string.fail_no_objects, Toast.LENGTH_SHORT).show()
			finish()
			return
		}
		newTest.setTested(
			TestFragment.amountText.toIntOrNull() ?: Test.getAmount(),
			{ sl ->
				if (test == null) return@setTested false
				runOnUiThread {
					if (sl > 0) secondsLeft = sl
					else onSubmit()
				}
				true
			},
			TestFragment.timeText.toIntOrNull() ?: Test.getDefaultTime(),
			paths,
		)
		for (sp in newTest.testSrc) list.add(TestItemModel(sp))
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
		}
		(list[0].sp.srcPath[0] as MainChapter).save()
		TestResultsPopup(list, success * 100f / list.size)
		reset()
	}

	private fun reset() {
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
		private var resumed = false

		private var test: Test<*>? = null
		private val list = ArrayList<TestItemModel>()

		/** The typed answers, keyed by item index (survives scrolling; was the View cache). */
		private val answers = mutableStateMapOf<Int, String>()

		private var secondsLeft by mutableIntStateOf(0)
	}
}

private val TimerRed = Color(0xFFDD0000)
