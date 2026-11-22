package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.ItemKind

/**
 * What one criterion compares.
 * - a new measure or field is one entry here plus its matcher in [SearchEngine].
 * - the entries go by importance, since the header menu lists them in that order.
 */
enum class SearchTag {
	NAME,
	TYPE,
	RATE,
	TESTED,
	DESCRIPTION;

	/** the value of a measure is a whole number */
	val numeric: Boolean get() = this == RATE || this == TESTED

	/** The comparisons this tag offers. */
	val operators: List<FilterOperator>
		get() = when (this) {
			TYPE -> KindOperators
			NAME, DESCRIPTION -> TextOperators
			RATE, TESTED -> NumberOperators
		}
}

/** How a criterion compares its value. */
enum class FilterOperator {
	IS,
	CONTAINS,
	CONTAINS_CASE,
	STARTS,
	ENDS,
	REGEX,
	GREATER,
	AT_LEAST,
	LESS,
	AT_MOST,
	EQUALS,
}

/** Whether a rate criterion counts the rows that were never tested. */
enum class UntestedRows {
	INCLUDE,
	EXCLUDE,
}

/**
 * The value of one criterion, its shape follows the tag.
 * - the editor builds it from labeled choices, so nothing here is parsed.
 */
sealed interface CriterionValue {

	/** the free text of a name or description */
	data class Text(val value: String) : CriterionValue

	/** answers of a row, where zero is its own value */
	data class Times(val value: Int) : CriterionValue

	/** success rate in percent, with the rule for the untested rows */
	data class Rate(val percent: Int, val untested: UntestedRows = UntestedRows.INCLUDE) : CriterionValue

	/** the item kind a search keeps */
	data class Kind(val value: ItemKind) : CriterionValue
}

/**
 * One filter: a tag, its comparison and its value.
 * - the editor is the only door, so an applied criterion always searches.
 */
data class Criterion(
	val tag: SearchTag,
	val operator: FilterOperator,
	val value: CriterionValue,
)

/**
 * What a run answers to: the typed text plus one criterion per tag.
 * - the field owns [text], the filter editor owns [criteria], and a run ANDs them.
 */
data class SearchQuery(
	val text: String = "",
	val criteria: List<Criterion> = emptyList(),
) {

	/** the criterion of a tag, null when the search leaves it open */
	fun criterionOf(tag: SearchTag) = criteria.firstOrNull { it.tag == tag }

	/** [criterion] replaces the one of its tag, so a tag never repeats */
	fun with(criterion: Criterion) = copy(criteria = criteria.filterNot { it.tag == criterion.tag } + criterion)

	/** the criterion of [tag] is gone, other tags stay */
	fun without(tag: SearchTag) = copy(criteria = criteria.filterNot { it.tag == tag })

	/** nothing searched, so the list shows its container */
	val isEmpty: Boolean get() = text.isBlank() && criteria.isEmpty()
}

private val KindOperators = listOf(FilterOperator.IS)

private val TextOperators = listOf(
	FilterOperator.CONTAINS, FilterOperator.CONTAINS_CASE,
	FilterOperator.STARTS, FilterOperator.ENDS, FilterOperator.REGEX,
)

private val NumberOperators = listOf(
	FilterOperator.GREATER, FilterOperator.AT_LEAST,
	FilterOperator.LESS, FilterOperator.AT_MOST, FilterOperator.EQUALS,
)
