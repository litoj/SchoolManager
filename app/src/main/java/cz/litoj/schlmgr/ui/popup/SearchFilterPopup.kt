package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.search.FilterCondition
import cz.litoj.schlmgr.ui.search.FilterField
import cz.litoj.schlmgr.ui.search.FilterType
import cz.litoj.schlmgr.ui.search.SearchFilter
import cz.litoj.schlmgr.ui.search.label
import java.util.regex.Pattern

/**
 * The visual search filter: labeled choices write the query that [SearchFilter] defines.
 * - a written query reopens on its filter, so the controls stay in charge of the search.
 * - the preview names the written form before it runs.
 */
class SearchFilterPopup(
	query: String,
	private val onApply: (SearchFilter?) -> Unit,
) : DialogSpec {

	private val initial = SearchFilter.parse(query)
		?: query.trim().takeIf { it.isNotEmpty() && !it.startsWith('\\') }
			?.let { SearchFilter(value = it) }
	private val startField = initial?.field ?: FilterField.NAME

	private var type by mutableStateOf(initial?.type ?: FilterType.ANY)
	private var field by mutableStateOf(startField)
	private var condition by mutableStateOf(
		(initial?.condition ?: FilterCondition.CONTAINS)
			.takeIf { it in startField.conditions } ?: startField.conditions.first(),
	)
	private var value by mutableStateOf(initial?.value ?: "")

	init {
		Dialogs.show(this)
	}

	private fun current(): SearchFilter? {
		val text = value.trim()
		if (text.isEmpty() || validation(text) != null) return null
		return SearchFilter(type, field, condition, text)
	}

	private fun validation(text: String = value.trim()): Int? {
		if (text.isEmpty()) return null
		if (field.numeric && text.toIntOrNull() == null) return R.string.filter_number_error
		if (condition == FilterCondition.REGEX && !compiles(text)) return R.string.filter_regex_error
		return null
	}

	private fun compiles(text: String): Boolean = try {
		Pattern.compile(text)
		true
	} catch (e: Exception) {
		false
	}

	@Composable
	override fun Content() {
		val filter = current()
		val problem = validation()
		PopupCard {
			PopupTitle(stringResource(R.string.search_filter))
			HairlineDivider()
			Column(
				Modifier
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState()),
			) {
				Text(
					stringResource(R.string.search_filter_intro),
					Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
					style = TextStyle(fontSize = 15.sp, color = TextColor),
				)
				ChoiceRow(
					label = stringResource(R.string.filter_type),
					options = FilterType.entries,
					selected = type,
					labelOf = { stringResource(it.label) },
					onPick = { type = it },
				)
				ChoiceRow(
					label = stringResource(R.string.filter_field),
					options = FilterField.entries,
					selected = field,
					labelOf = { stringResource(it.label) },
					onPick = {
						field = it
						condition = condition.takeIf { c -> c in it.conditions } ?: it.conditions.first()
					},
				)
				ChoiceRow(
					label = stringResource(R.string.filter_condition),
					options = field.conditions,
					selected = condition,
					labelOf = { stringResource(it.label) },
					onPick = { condition = it },
				)
				Text(
					stringResource(R.string.filter_value),
					Modifier.padding(start = 20.dp, top = 5.dp),
					style = TextStyle(fontSize = 15.sp, color = TextColor),
				)
				BorderedTextField(
					value = value,
					onValueChange = { value = it },
					label = stringResource(
						if (field.numeric) R.string.filter_value_number else R.string.filter_value_text
					),
					textColor = TextColor,
					singleLine = true,
					keyboardType = if (field.numeric) KeyboardType.Number else KeyboardType.Text,
				)
			}
			if (problem != null) Text(
				stringResource(problem),
				Modifier.padding(horizontal = 20.dp),
				style = TextStyle(fontSize = 13.sp, color = colorResource(R.color.colorFailure)),
			)
			filter?.let {
				Text(
					stringResource(R.string.filter_preview, it.toQuery()),
					Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
					style = TextStyle(
						fontSize = 13.sp,
						fontFamily = FontFamily.Monospace,
						color = TextColor,
					),
				)
			}
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) {
					Dialogs.dismiss(this@SearchFilterPopup)
				}
				if (initial != null) {
					VerticalButtonSeparator()
					PopupButton(stringResource(R.string.filter_clear), Modifier.weight(1f)) {
						Dialogs.dismiss(this@SearchFilterPopup)
						onApply(null)
					}
				}
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.filter_apply), Modifier.weight(1f), enabled = filter != null) {
					Dialogs.dismiss(this@SearchFilterPopup)
					onApply(filter)
				}
			}
		}
	}
}

/** A labeled row of choices, which wraps so no option hides off the edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
	label: String,
	options: List<T>,
	selected: T,
	labelOf: @Composable (T) -> String,
	onPick: (T) -> Unit,
) {
	Text(
		label,
		Modifier.padding(start = 20.dp, top = 5.dp),
		style = TextStyle(fontSize = 15.sp, color = TextColor),
	)
	FlowRow(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp),
	) {
		options.forEach { option ->
			val on = option == selected
			Box(
				Modifier
					.padding(end = 4.dp, bottom = 2.dp)
					.background(if (on) colorResource(R.color.colorSelectedRow) else Color.Transparent)
					.clickable { onPick(option) }
					.defaultMinSize(minHeight = 36.dp)
					.padding(horizontal = 8.dp, vertical = 3.dp),
				contentAlignment = Alignment.Center,
			) {
				Text(labelOf(option), style = TextStyle(fontSize = 15.sp, color = TextColor))
			}
		}
	}
}
