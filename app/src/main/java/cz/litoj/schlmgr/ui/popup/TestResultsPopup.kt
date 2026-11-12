package cz.litoj.schlmgr.ui.popup

import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import cz.litoj.schlmgr.testing.AnswerComparison
import cz.litoj.schlmgr.testing.AnswerMark
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.component.ExplorerColors
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * Post-test results: the success percentage and one explorer-style row per tested item.
 * - the prompt of the run is the headline.
 * - the result order replaces the explorer position.
 * - the marked typed answer and labelled expected answer show below the headline.
 * - green marks matches, grey marks missing letters and red marks extra letters.
 * - passed/total ends the row.
 * - the kind icon drops out, a test only ever holds words.
 *
 * @param success success percentage from 0 to 100, not a fraction
 */
class TestResultsPopup(items: List<TestItemModel>, private val success: Float) : DialogSpec {

	private class ResultRow(
		val model: ItemUiModel,
		val expected: String,
		val comparisons: List<AnswerComparison>,
		val hasAnswer: Boolean,
		val correct: Boolean,
		val hasVariants: Boolean,
		val passed: Int,
		val tested: Int,
	)

	// the setting can change while the report stays open, so the rows keep the direction of the run
	private val reversed = TestEngine.isReversed

	// built eagerly, since reset clears the tested list right after this constructor
	private val rows: List<ResultRow> = items.mapIndexed { index, item -> toRow(item, index + 1) }

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		val successText = stringResource(
			R.string.test_score,
			rows.count { it.correct },
			rows.size,
			Math.round(success),
		)
		// one width for every result index, so the rows render at the browse number size.
		// the widest count can lie below the largest one, since digit widths differ.
		val positionWidth = positionColumnWidth(
			rows.maxOfOrNull { it.model.position } ?: 1,
			ExplorerRowVariant.Browse,
		)
		PopupCard {
			PopupTitle(successText)
			HairlineDivider()
			LazyColumn(Modifier.weight(1f, fill = false)) {
				items(rows) { row -> WordResultRow(row, positionWidth) }
			}
			HairlineDivider()
			PopupButtonRow {
				val context = LocalContext.current
				PopupButton(stringResource(R.string.test_retake), Modifier.weight(1f)) {
					Dialogs.dismiss(this@TestResultsPopup)
					// the setup keeps its sources, so the run rebuilds on the same ones
					context.startActivity(Intent(context, TestActivity::class.java))
				}
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) { Dialogs.dismiss(this@TestResultsPopup) }
			}
		}
	}

	@Composable
	private fun WordResultRow(row: ResultRow, positionWidth: Dp) {
		Item(
			row.model,
			onClick = {},
			onLongClick = {},
			descVisible = false,
			iconVisible = false,
			positionWidth = positionWidth,
			// the labels need string resources, and the model is built outside composition.
			// this slot also ignores the description limit, so no translation collapses.
			answer = {
				AnswerText(row)
				// a right guess already names the answer, unless variants need the full word
				if (row.expected.isNotEmpty() && (!row.correct || row.hasVariants)) Text(
					stringResource(R.string.test_correct, row.expected),
					style = TextStyle(fontSize = 15.sp, color = ExplorerColors.description),
				)
			},
			trailing = {
				Text(
					"${row.passed}/${row.tested}",
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			},
		)
	}

	@Composable
	private fun AnswerText(row: ResultRow) {
		val body = ExplorerColors.body
		val correct = ExplorerColors.header
		val missing = body.copy(alpha = 0.45f)
		val extra = ExplorerColors.failure
		val prefix = stringResource(R.string.test_your_answer, "")
		val noAnswer = stringResource(R.string.test_no_answer)
		Text(
			buildAnnotatedString {
				append(prefix)
				if (!row.hasAnswer) withStyle(SpanStyle(color = extra)) { append(noAnswer) }
				else row.comparisons.forEachIndexed { index, comparison ->
					if (index > 0) append("/")
					for (part in comparison.parts) {
						val color = when (part.mark) {
							AnswerMark.CORRECT -> correct
							AnswerMark.MISSING -> missing
							AnswerMark.EXTRA -> extra
						}
						withStyle(SpanStyle(color = color)) { append(part.text) }
					}
				}
			},
			style = TextStyle(fontSize = 15.sp, color = body),
		)
	}

	private fun toRow(item: TestItemModel, position: Int): ResultRow {
		val parsedWord = ItemRow.nameParser(item.word.name)
		val translations = item.translations.joinToString("\n") { ItemRow.nameParser(it.name) }
		val headline = if (reversed) parsedWord else translations
		val expected = if (reversed) translations else parsedWord
		val names = if (reversed) item.translations.map { it.name } else listOf(item.word.name)
		val targets = names.flatMap { name ->
			WordNames.parseVariants(name).map { it.trim() }
		}.distinct()
		val comparisons = item.answers.map { answer ->
			// linked synonyms pass without being a target whose spelling should be marked wrong
			if (item.correct) AnswerComparison.compare(answer, answer)
			else AnswerComparison.closest(answer, targets)
		}
		return ResultRow(
			toModel(item.word, headline, position),
			expected,
			comparisons,
			item.answers.any { it.isNotBlank() },
			item.correct,
			item.word.name.contains('/'),
			item.word.passedTests,
			item.word.testedCount,
		)
	}

	/** The explorer-row model of one result, a plain unflipped row with the counters of the word. */
	private fun toModel(word: DbItem, headline: String, position: Int) = ItemUiModel(
		key = word,
		position = position,
		name = headline,
		description = "",
		kind = RowKind.WORD,
		ratio = word.ratio,
		isReference = false,
		selected = false,
	)
}
