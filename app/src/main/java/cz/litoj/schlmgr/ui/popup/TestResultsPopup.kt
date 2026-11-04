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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
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
 * - the tested count replaces the position.
 * - the labelled expected answer and the typed answer show below the headline.
 * - passed/total ends the row.
 * - the kind icon drops out, a test only ever holds words.
 *
 * @param success success percentage from 0 to 100, not a fraction
 */
class TestResultsPopup(items: List<TestItemModel>, private val success: Float) : DialogSpec {

	private class ResultRow(
		val model: ItemUiModel,
		val expected: String,
		val answer: String,
		val correct: Boolean,
		val passed: Int,
	)

	// the setting can change while the report stays open, so the rows keep the direction of the run
	private val reversed = TestEngine.isReversed

	// built eagerly, since reset clears the tested list right after this constructor
	private val rows: List<ResultRow> = items.map(::toRow)

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
		// one width for every count cell, so the rows render at the browse number size.
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
				// a right guess already names the answer, so the correct line stays for the rest
				if (!row.correct && row.expected.isNotEmpty()) Text(
					stringResource(R.string.test_correct, row.expected),
					style = TextStyle(fontSize = 15.sp, color = ExplorerColors.description),
				)
				Text(
					stringResource(
						R.string.test_your_answer,
						row.answer.ifBlank { stringResource(R.string.test_no_answer) },
					),
					style = TextStyle(
						fontSize = 15.sp,
						color = if (row.correct) colorResource(R.color.colorPrimaryHeader)
						else colorResource(R.color.colorFailure)
					),
				)
			},
			trailing = {
				Text(
					"${row.passed}/${row.model.position}",
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			},
		)
	}

	private fun toRow(item: TestItemModel): ResultRow {
		val parsedWord = ItemRow.nameParser(item.word.name)
		val translations = item.translations.joinToString("\n") { ItemRow.nameParser(it.name) }
		val headline = if (reversed) parsedWord else translations
		val expected = if (reversed) translations else parsedWord
		return ResultRow(
			toModel(item.word, headline),
			expected,
			item.answer,
			item.correct,
			item.word.passedTests,
		)
	}

	/** The explorer-row model of one result, a plain unflipped row with the counters of the word. */
	private fun toModel(word: DbItem, headline: String) = ItemUiModel(
		key = word,
		position = word.testedCount,
		name = headline,
		description = "",
		kind = RowKind.WORD,
		ratio = word.ratio,
		isReference = false,
		selected = false,
	)
}
