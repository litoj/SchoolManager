package cz.litoj.schlmgr.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.ItemKind

/** The label of a tag. */
val SearchTag.label: Int
	get() = when (this) {
		SearchTag.TYPE -> R.string.filter_tag_type
		SearchTag.NAME -> R.string.filter_tag_name
		SearchTag.DESCRIPTION -> R.string.filter_tag_description
		SearchTag.RATE -> R.string.filter_tag_rate
		SearchTag.TESTED -> R.string.filter_tag_tested
	}

/** The line that names what a tag counts, 0 where the label says it. */
val SearchTag.hint: Int
	get() = when (this) {
		SearchTag.RATE -> R.string.filter_tag_rate_hint
		SearchTag.TESTED -> R.string.filter_tag_tested_hint
		else -> 0
	}

/** The kinds the type tag offers, the invisible root among them never. */
val SearchTag.kinds: List<ItemKind>
	get() = if (this == SearchTag.TYPE) SearchKinds else emptyList()

/** The label of a comparison. */
val FilterOperator.label: Int
	get() = when (this) {
		FilterOperator.IS -> 0
		FilterOperator.CONTAINS -> R.string.filter_op_contains
		FilterOperator.CONTAINS_CASE -> R.string.filter_op_contains_case
		FilterOperator.STARTS -> R.string.filter_op_starts
		FilterOperator.ENDS -> R.string.filter_op_ends
		FilterOperator.REGEX -> R.string.filter_op_regex
		FilterOperator.GREATER -> R.string.filter_op_greater
		FilterOperator.AT_LEAST -> R.string.filter_op_at_least
		FilterOperator.LESS -> R.string.filter_op_less
		FilterOperator.AT_MOST -> R.string.filter_op_at_most
		FilterOperator.EQUALS -> R.string.filter_op_equals
	}

/** The label of the untested choice of a rate criterion. */
val UntestedRows.label: Int
	get() = when (this) {
		UntestedRows.INCLUDE -> R.string.filter_untested_in
		UntestedRows.EXCLUDE -> R.string.filter_untested_out
	}

/** The name of a kind in the type choice, 0 for a kind no tag offers. */
val ItemKind.label: Int
	get() = when (this) {
		ItemKind.WORD -> R.string.filter_kind_word
		ItemKind.TRANSLATION -> R.string.filter_kind_translation
		ItemKind.CHAPTER -> R.string.filter_kind_chapter
		ItemKind.NOTE -> R.string.filter_kind_note
		ItemKind.ROOT -> 0
	}

/** The one line of one criterion, shown on its chip and in its editor. */
@Composable
fun Criterion.summary(): String {
	val line = when (val value = value) {
		is CriterionValue.Kind -> stringResource(
			R.string.filter_chip_kind,
			stringResource(tag.label),
			stringResource(value.value.label),
		)
		is CriterionValue.Text -> chipLine(value.value)
		is CriterionValue.Times -> chipLine(value.value.toString())
		is CriterionValue.Rate -> chipLine(value.percent.toString()) +
			// the run of a rate names the rows it leaves out
			if (value.untested == UntestedRows.EXCLUDE)
				" · " + stringResource(R.string.filter_chip_untested_out)
			else ""
	}
	return line
}

@Composable
private fun Criterion.chipLine(value: String) = stringResource(
	R.string.filter_chip,
	stringResource(tag.label),
	stringResource(operator.label),
	value,
)

private val SearchKinds = listOf(
	ItemKind.WORD, ItemKind.TRANSLATION, ItemKind.CHAPTER, ItemKind.NOTE,
)
