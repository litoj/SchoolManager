package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.ratio
import cz.litoj.schlmgr.db.sfCount
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.ui.explorer.components.Item
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import cz.litoj.schlmgr.ui.explorer.model.ItemUiModel

/**
 * The post-test results: success percentage and every item rendered like an
 * explorer word row — the word's translations in place of the description, its
 * appearance count in place of the position number, the typed answer under them
 * and the word's success rate (`successful/total`) at the row's end.
 */
class TestResultsPopup(items: ArrayList<TestItemModel>, success: Float) : DialogSpec {

	/** One result and its prebuilt explorer-row model (the rows never change). */
	private class ResultRow(val item: TestItemModel, val model: ItemUiModel)

	@Suppress("UNCHECKED_CAST")
	private val rows: List<ResultRow> =
		(items.clone() as List<TestItemModel>).map { ResultRow(it, toModel(it)) }
	private val successText = GlobalDependencies.appContext.getString(R.string.success_rate, success.toString())

	/** Digit count of the largest appearance count, so the count cells line up. */
	private val countDigits = (rows.maxOfOrNull { it.model.position } ?: 0).toString().length

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
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
				Spacer(
					Modifier
						.fillMaxWidth()
						.height(0.7.dp)
						.background(colorResource(R.color.colorStripDivider))
				)
				LazyColumn(Modifier.weight(1f)) {
					items(rows) { row -> WordResultRow(row) }
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
				Text("OK", style = TextStyle(fontSize = 15.sp))
			}
		}
	}

	@Composable
	private fun WordResultRow(row: ResultRow) {
		Item(
			row.model,
			onClick = {},
			onLongClick = {},
			descVisible = true,
			positionDigits = countDigits,
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
					"${row.item.word.passedTests}/${row.item.word.sfCount}",
					style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
				)
			},
		)
	}

	/** The explorer-row model of one result: the word as a plain, unflipped word row. */
	private fun toModel(item: TestItemModel) = ItemUiModel(
		key = item,
		position = item.word.sfCount,
		name = ItemRow.nameParser(item.word.name),
		desc = item.translations.joinToString("\n") { ItemRow.nameParser(it.name) },
		iconRes = R.drawable.ic_word,
		ratio = item.word.ratio,
		isReference = false,
		isWord = true,
		flipped = false,
		selected = false,
		payload = ItemRow(item.word, null, 0, false),
	)
}
