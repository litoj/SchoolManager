package cz.litoj.schlmgr.ui.popup

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * Post-test results: the success percentage and one explorer-style row per tested item.
 * - translations replace the description.
 * - the tested count replaces the position.
 * - the typed answer shows below, with passed/total at the end of the row.
 *
 * @param success success percentage from 0 to 100, not a fraction
 */
class TestResultsPopup(items: List<TestItemModel>, private val success: Float) : DialogSpec {

	private class ResultRow(val item: TestItemModel, val model: ItemUiModel)

	// built eagerly, since reset clears the tested list right after this constructor
	private val rows: List<ResultRow> = items.map { ResultRow(it, toModel(it)) }

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		val correct = rows.count { it.item.correct }
		val successText = stringResource(
			R.string.success_rate,
			Math.round(success).toInt(),
			correct,
			rows.size,
		)
		// one width for every count cell, so the rows render at the browse number size.
		// the widest count can lie below the largest one, since digit widths differ.
		val positionWidth = positionColumnWidth(
			rows.size,
			ExplorerRowVariant.Browse,
		)
		Box(Modifier.fillMaxSize().padding(25.dp)) {
			Column(
				Modifier
					.fillMaxWidth()
					.background(colorResource(R.color.colorPrimaryBg))
					.padding(5.dp)
			) {
				PopupTitle(successText)
				HairlineDivider()
				LazyColumn(Modifier.weight(1f)) {
					itemsIndexed(rows) { index, row -> WordResultRow(index, row, positionWidth) }
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
	}

	@Composable
	private fun WordResultRow(index: Int, row: ResultRow, positionWidth: Dp) {
		Item(
			row.model.copy(position = index + 1),
			onClick = {},
			onLongClick = {},
			descVisible = true,
			positionWidth = positionWidth,
			answer = {
				// the run outcome leads, the expected side follows on a wrong answer
				if (row.item.correct) {
					Text(
						"✓ " + row.item.answer,
						style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryHeader)),
					)
				} else {
					Text(
						stringResource(R.string.test_you, row.item.answer),
						style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorFailure)),
					)
					Text(
						stringResource(R.string.test_expected, expectedOf(row.item)),
						style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorFailure)),
					)
				}
			},
			trailing = {
				Text(
					"${row.item.word.passedTests}/${row.item.word.testedCount}",
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			},
		)
	}

	/** The asked side of the run, which a wrong answer failed to name. */
	private fun expectedOf(item: TestItemModel): String =
		if (TestEngine.isReversed) item.translations.joinToString("\n") { ItemRow.nameParser(it.name) }
		else ItemRow.nameParser(item.word.name)

	/** The explorer-row model of one result, the word as a plain unflipped row. */
	private fun toModel(item: TestItemModel) = ItemUiModel(
		key = item,
		position = item.word.testedCount,
		name = ItemRow.nameParser(item.word.name),
		description = item.translations.joinToString("\n") { ItemRow.nameParser(it.name) },
		kind = RowKind.WORD,
		ratio = item.word.ratio,
		isReference = false,
		selected = false,
	)
}
