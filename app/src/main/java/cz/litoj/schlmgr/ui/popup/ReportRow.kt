package cz.litoj.schlmgr.ui.popup

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.viewmodel.RowView

/**
 * The report form of one tested word, an explorer row ending with its counters.
 * - the help renders its samples through it too, so a sample cannot drift from a result.
 * - [answer] shows below the headline, the caller building the labels it needs.
 */
@Composable
internal fun ReportRow(
	row: RowView,
	passed: Int,
	tested: Int,
	positionWidth: Dp,
	answer: (@Composable () -> Unit)? = null,
) {
	Item(
		row,
		onClick = {},
		onLongClick = {},
		descVisible = false,
		positionWidth = positionWidth,
		answer = answer,
		trailing = {
			Text(
				"$passed/$tested",
				style = TextStyle(fontSize = 15.sp, color = colorResource(R.color.colorPrimaryFg)),
			)
		},
	)
}
