package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import cz.litoj.schlmgr.ui.search.hint
import cz.litoj.schlmgr.ui.search.kinds
import cz.litoj.schlmgr.ui.search.label
import cz.litoj.schlmgr.ui.search.summary
import java.util.regex.Pattern

/**
 * The editor of one search criterion, which the tag of [existing] decides.
 * - a new criterion picks its tag first, so one dialog serves every tag.
 * - [query] supplies the filter a tag already has, so picking it back reopens its value.
 * - Apply is held until the value fits the tag, so a criterion never fails to search.
 * - [startTag] opens a new criterion on that tag, the tag a header menu pick names.
 */
class CriterionPopup(
	private val query: SearchQuery,
	private val existing: Criterion?,
	private val onApply: (Criterion) -> Unit,
	private val onDrop: (SearchTag) -> Unit,
	startTag: SearchTag? = null,
) : DialogSpec {

	private var tag by mutableStateOf(existing?.tag ?: SearchTag.TYPE)
	private var operator by mutableStateOf(existing?.operator ?: tag.operators.first())
	private var kind by mutableStateOf(existing?.kindValue ?: ItemKind.WORD)
	private var untested by mutableStateOf(existing?.untestedValue ?: UntestedRows.INCLUDE)
	private var value by mutableStateOf(existing?.startText ?: "")

	init {
		if (existing == null && startTag != null) pickTag(startTag)
		Dialogs.show(this)
	}

	/** The choices move to the value [picked] already has, empty when it has none. */
	private fun pickTag(picked: SearchTag) {
		tag = picked
		val stored = query.criterionOf(picked)
		if (stored == null) {
			operator = picked.operators.first()
			value = ""
			return
		}
		operator = stored.operator
		kind = stored.kindValue ?: kind
		untested = stored.untestedValue ?: UntestedRows.INCLUDE
		value = stored.startText
	}

	/** the criterion the choices describe, null while the value does not fit the tag */
	private fun current(): Criterion? {
		if (validation() != null) return null
		val text = value.trim()
		val built = when (tag) {
			SearchTag.TYPE -> CriterionValue.Kind(kind)
			SearchTag.RATE -> text.toIntOrNull()?.let { CriterionValue.Rate(it, untested) } ?: return null
			SearchTag.TESTED -> text.toIntOrNull()?.let { CriterionValue.Times(it) } ?: return null
			else -> CriterionValue.Text(text)
		}
		return Criterion(tag, operator, built)
	}

	private fun validation(): Int? {
		val text = value.trim()
		if (tag == SearchTag.TYPE) return null
		if (tag.numeric) return if (text.toIntOrNull() == null) R.string.filter_number_error else null
		if (text.isEmpty()) return R.string.filter_value_missing
		if (operator == FilterOperator.REGEX && !compiles(text)) return R.string.filter_regex_error
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
		val criterion = current()
		val problem = validation()
		val title = if (existing == null) stringResource(R.string.search_filter)
		else stringResource(tag.label)
		PopupCard {
			PopupTitle(title)
			HairlineDivider()
			PopupScrollBody {
				if (existing == null) {
					Text(
						stringResource(R.string.search_filter_intro),
						Modifier.padding(horizontal = 15.dp, vertical = 5.dp),
						style = TextStyle(fontSize = 15.sp, color = TextColor),
					)
					ChoiceRow(
						label = stringResource(R.string.filter_tag),
						options = SearchTag.entries,
						selected = tag,
						labelOf = { stringResource(it.label) },
						onPick = ::pickTag,
					)
				}
				if (tag.operators.size > 1) ChoiceRow(
					label = stringResource(R.string.filter_condition),
					options = tag.operators,
					selected = operator,
					labelOf = { stringResource(it.label) },
					onPick = { operator = it },
				)
				if (tag.kinds.isNotEmpty()) ChoiceRow(
					label = stringResource(R.string.filter_kind),
					options = tag.kinds,
					selected = kind,
					labelOf = { stringResource(it.label) },
					onPick = { kind = it },
				)
				if (tag.hint != 0) TagHint(stringResource(tag.hint))
				// only the rate has a row without one, a count keeps zero as its own value
				if (tag == SearchTag.RATE) ChoiceRow(
					label = stringResource(R.string.filter_untested),
					options = UntestedRows.entries,
					selected = untested,
					labelOf = { stringResource(it.label) },
					onPick = { untested = it },
				)
				if (tag != SearchTag.TYPE) {
					Text(
						stringResource(R.string.filter_value),
						Modifier.padding(start = 15.dp, top = 5.dp),
						style = TextStyle(fontSize = 15.sp, color = TextColor),
					)
					BorderedTextField(
						value = value,
						onValueChange = { value = it },
						label = stringResource(
							if (tag.numeric) R.string.filter_value_number else R.string.filter_value_text
						),
						textColor = TextColor,
						singleLine = true,
						keyboardType = if (tag.numeric) KeyboardType.Number else KeyboardType.Text,
					)
				}
			}
			if (problem != null) Text(
				stringResource(problem),
				Modifier.padding(horizontal = 20.dp),
				style = TextStyle(fontSize = 13.sp, color = colorResource(R.color.colorFailure)),
			)
			// the line names the filter the same way its chip will
			criterion?.let {
				Text(
					it.summary(),
					Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
					style = TextStyle(fontSize = 13.sp, color = TextColor),
				)
			}
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) {
					Dialogs.dismiss(this@CriterionPopup)
				}
				if (existing != null) {
					VerticalButtonSeparator()
					PopupButton(stringResource(R.string.filter_clear), Modifier.weight(1f)) {
						Dialogs.dismiss(this@CriterionPopup)
						onDrop(existing.tag)
					}
				}
				VerticalButtonSeparator()
				PopupButton(
					stringResource(R.string.filter_apply), Modifier.weight(1f), enabled = criterion != null,
				) {
					Dialogs.dismiss(this@CriterionPopup)
					criterion?.let(onApply)
				}
			}
		}
	}
}

/** The value box text a stored criterion starts with. */
private val Criterion.startText: String
	get() = when (val value = value) {
		is CriterionValue.Text -> value.value
		is CriterionValue.Times -> value.value.toString()
		is CriterionValue.Rate -> value.percent.toString()
		is CriterionValue.Kind -> ""
	}

private val Criterion.kindValue: ItemKind?
	get() = (value as? CriterionValue.Kind)?.value

private val Criterion.untestedValue: UntestedRows?
	get() = (value as? CriterionValue.Rate)?.untested

/** The line that names what a tag counts. */
@Composable
private fun TagHint(text: String) {
	Text(
		text,
		Modifier.padding(horizontal = 15.dp),
		style = TextStyle(fontSize = 13.sp, color = TextColor),
	)
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
		Modifier.padding(start = 15.dp, top = 5.dp),
		style = TextStyle(fontSize = 15.sp, color = TextColor),
	)
	FlowRow(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 15.dp),
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
