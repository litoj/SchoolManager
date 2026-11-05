package cz.litoj.schlmgr.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.litoj.schlmgr.R

/** The label of a filter type. */
val FilterType.label: Int
	get() = when (this) {
		FilterType.ANY -> R.string.filter_type_any
		FilterType.WORD -> R.string.filter_type_word
		FilterType.TRANSLATION -> R.string.filter_type_translation
		FilterType.CHAPTER -> R.string.filter_type_chapter
	}

/** The label of a compared field. */
val FilterField.label: Int
	get() = when (this) {
		FilterField.NAME -> R.string.filter_field_name
		FilterField.DESCRIPTION -> R.string.filter_field_description
		FilterField.SUCCESSES -> R.string.filter_field_successes
		FilterField.FAILS -> R.string.filter_field_fails
		FilterField.TESTED -> R.string.filter_field_tested
		FilterField.RATE -> R.string.filter_field_rate
	}

/** The label of a condition. */
val FilterCondition.label: Int
	get() = when (this) {
		FilterCondition.CONTAINS -> R.string.filter_cond_contains
		FilterCondition.CONTAINS_CASE -> R.string.filter_cond_contains_case
		FilterCondition.STARTS -> R.string.filter_cond_starts
		FilterCondition.ENDS -> R.string.filter_cond_ends
		FilterCondition.REGEX -> R.string.filter_cond_regex
		FilterCondition.GREATER -> R.string.filter_cond_greater
		FilterCondition.LESS -> R.string.filter_cond_less
		FilterCondition.EQUALS -> R.string.filter_cond_equals
	}

/** The one-line description of an active filter, used by the info strip. */
@Composable
fun SearchFilter.summary(): String = stringResource(
	R.string.filter_summary,
	stringResource(type.label),
	stringResource(field.label),
	stringResource(condition.label),
	value,
)
