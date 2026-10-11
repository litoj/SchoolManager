package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * Post-test results — the success percentage and one explorer-style row
 * per tested item.
 * - translations replace the description. The tested count replaces the
 *   position.
 * - the typed answer below, `passed/total` at the row's end.
 *
 * @param success success percentage 0–100 (not a fraction)
 */
class TestResultsPopup(items: List<TestItemModel>, private val success: Float) : DialogSpec {

	private class ResultRow(val item: TestItemModel, val model: ItemUiModel)

	// built eagerly — reset() clears the tested list right after this constructor
	private val rows: List<ResultRow> = items.map { ResultRow(it, toModel(it)) }

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		val successText = stringResource(R.string.success_rate, success.toString())
		// One width for every count cell (the widest count shown), so the rows render at
		// the browse number size, and the widest count can lie below the largest one.
		val positionWidth = positionColumnWidth(
			rows.maxOfOrNull { it.model.position } ?: 1,
			ExplorerRowVariant.Browse,
		)
		Box(Modifier.fillMaxSize().padding(25.dp)) {
			Column(
				Modifier
					.fillMaxWidth()
					.background(colorResource(R.color.colorPrimaryBg))
					.padding(5.dp)
			) {
				Text(
					successText,
					Modifier
						.fillMaxWidth()
						.padding(top = 5.dp),
					style = TextStyle(fontSize = 25.sp, color = colorResource(R.color.colorPrimaryFg)),
					textAlign = TextAlign.Center,
				)
				HairlineDivider()
				LazyColumn(Modifier.weight(1f)) {
					items(rows) { row -> WordResultRow(row, positionWidth) }
				}
			}
			Button(
				onClick = { Dialogs.dismiss(this@TestResultsPopup) },
				// The app theme colours (header green background, white text) — the
				// Material default would be the Compose purple, not this app's theme.
				colors = ButtonDefaults.buttonColors(
					backgroundColor = colorResource(R.color.colorPrimaryHeader),
					contentColor = colorResource(R.color.colorOnHeader),
				),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 10.dp)
					.height(30.dp),
			) {
				Text(stringResource(R.string.ok), style = TextStyle(fontSize = 15.sp))
			}
		}
	}

	@Composable
	private fun WordResultRow(row: ResultRow, positionWidth: Dp) {
		Item(
			row.model,
			onClick = {},
			onLongClick = {},
			descVisible = true,
			positionWidth = positionWidth,
			answer = {
				if (row.item.answer.isNotEmpty()) Text(
					row.item.answer,
					style = TextStyle(
						fontSize = 15.sp,
						color = if (row.item.correct) colorResource(R.color.colorPrimaryHeader)
						else colorResource(R.color.colorFailure)
					),
				)
			},
			trailing = {
				Text(
					"${row.item.word.passedTests}/${row.item.word.testedCount}",
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			},
		)
	}

	/** The explorer-row model of one result — the word as a plain, unflipped word row. */
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
