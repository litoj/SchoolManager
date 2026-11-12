package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.launch

/** The side the run shows: the word reversed, its translations by default. */
internal fun promptItems(word: DbItem, translations: List<DbItem>): List<DbItem> =
	if (TestEngine.isReversed) listOf(word) else translations

/**
 * The prompt of one row, parsed like the explorer rows.
 * - every shown name carries its own description, the hidden answer never describes itself.
 */
@Composable
internal fun PromptLines(items: List<DbItem>, modifier: Modifier = Modifier) {
	val color = colorResource(R.color.colorPrimaryFg)
	Column(modifier) {
		items.forEach { shown ->
			Text(
				ItemRow.nameParser(shown.name),
				style = TextStyle(fontSize = 17.sp, color = color),
			)
			val description = shown.description.substringBefore('\n').trim()
			if (description.isNotEmpty()) Text(
				description,
				style = TextStyle(fontSize = 13.sp, color = color),
			)
		}
	}
}

/** One shared prompt and the answer fields that must all differ. */
@Composable
internal fun SharedPromptRow(
	task: TestTaskModel,
	pendingAnswer: Int,
	answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
	listState: LazyListState, setPendingAnswer: (Int) -> Unit,
) {
	Column(Modifier.fillMaxWidth()) {
		Column(Modifier.fillMaxWidth().padding(5.dp)) {
			val shown = task.members.first()
			Row(verticalAlignment = Alignment.CenterVertically) {
				PromptLines(promptItems(shown.word, shown.translations), Modifier.weight(1f))
				Text(
					pluralStringResource(R.plurals.test_synonyms, task.answerFields.size, task.answerFields.size),
					style = TextStyle(fontSize = 13.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			}
			// a required variant can repeat across two words, so only the ordinary block asks for different texts
			if (!TestEngine.requireAllVariants) Text(
				stringResource(R.string.test_answers_different),
				style = TextStyle(fontSize = 13.sp, color = colorResource(R.color.colorPrimaryFg)),
			)
		}
		task.answerFields.forEachIndexed { offset, _ ->
			val index = task.startIndex + offset
			LaunchedEffect(index, pendingAnswer) {
				if (pendingAnswer == index) {
					setPendingAnswer(-1)
					runCatching { answerFocus[index]?.requestFocus() }
				}
			}
			AnswerField(
				index,
				when {
					TestEngine.isReversed && TestEngine.requireAllVariants -> R.string.test_variants_translation
					TestEngine.isReversed -> R.string.data_translate
					TestEngine.requireAllVariants -> R.string.test_variants_word
					else -> R.string.data_word
				},
				answerFocus, index == TestRunState.items.lastIndex,
				listState, setPendingAnswer,
			)
		}
		if (!isLast) HairlineDivider(Modifier.padding(top = 2.dp))
	}
}

/** One answer field. [isLast] hides the keyboard on submit instead of advancing focus. */
@Composable
internal fun AnswerField(
	index: Int, hintRes: Int,
	answerFocus: HashMap<Int, FocusRequester>, isLast: Boolean,
	listState: LazyListState, setPendingAnswer: (Int) -> Unit,
) {
	val focusRequester = remember(index) { answerFocus.getOrPut(index) { FocusRequester() } }
	val keyboard = LocalSoftwareKeyboardController.current
	val scope = rememberCoroutineScope()
	WideTextField(
		value = TestRunState.answers[index] ?: "",
		onValueChange = { TestRunState.setAnswer(index, it) },
		fontSize = 15.sp,
		hint = stringResource(hintRes),
		focusRequester = focusRequester,
		imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
		onImeAction = {
			if (isLast) keyboard?.hide()
			else {
				val next = answerFocus[index + 1]
				val landed = next != null && runCatching { next.requestFocus() }.isSuccess
				if (!landed) {
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
