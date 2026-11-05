package cz.litoj.schlmgr.ui.search

import java.util.regex.Pattern

/**
 * A structured search condition, which writes the prefix syntax of [SearchEngine].
 * - the filter dialog owns the choices, the field shows the written query.
 * - [parse] reads a written query back, so the dialog can reopen on the active filter.
 */
data class SearchFilter(
	val type: FilterType = FilterType.ANY,
	val field: FilterField = FilterField.NAME,
	val condition: FilterCondition = FilterCondition.CONTAINS,
	val value: String = "",
) {

	/** The written query of this filter, accepted by [SearchEngine]. */
	fun toQuery(): String {
		val text = value.trim()
		// a leading operator would turn a name query into a number query, so a name
		// value that starts with one falls back to an equivalent regex form
		if (field == FilterField.NAME && text.firstOrNull() in OperatorChars) {
			val quoted = Pattern.quote(text)
			return when (condition) {
				FilterCondition.CONTAINS -> "\\" + type.marker + "\\" + text
				FilterCondition.CONTAINS_CASE ->
					"\\" + type.marker + "r.*" + quoted + ".*"
				FilterCondition.STARTS -> "\\" + type.marker + "r^" + quoted
				FilterCondition.ENDS -> "\\" + type.marker + "r.*" + quoted
				FilterCondition.REGEX -> "\\" + type.marker + "r(?:" + text + ")"
				else -> "\\" + type.marker + field.marker + condition.marker + text
			}
		}
		// the first option is a plain text search, so it stays free of markers
		return if (type == FilterType.ANY && field == FilterField.NAME &&
			condition == FilterCondition.CONTAINS
		) text
		else "\\" + type.marker + field.marker + condition.marker + text
	}

	companion object {

		private val NUMERIC = Regex("""^\\([WTC]?)([SFNR])([<>=])(\d+)$""")
		private val TEXT = Regex("""^\\([WTC]?)(D?)([\\cser]?)(.*)$""", RegexOption.DOT_MATCHES_ALL)

		/**
		 * The filter behind a written query, null for plain text or an unknown form.
		 * - a missing condition marker reads as contains, like the engine reads it.
		 */
		fun parse(query: String): SearchFilter? {
			val text = query.trim()
			if (!text.startsWith('\\')) return null
			NUMERIC.matchEntire(text)?.let { match ->
				val (type, field, condition, value) = match.destructured
				return SearchFilter(
					FilterType.of(type), FilterField.of(field), FilterCondition.of(condition), value.trim(),
				)
			}
			TEXT.matchEntire(text)?.let { match ->
				val (type, field, condition, value) = match.destructured
				val trimmedValue = value.trim()
				// a leading operator without a condition marker is a number query
				if (trimmedValue.isEmpty() || (condition.isEmpty() && trimmedValue.first() in "><=")) return null
				return SearchFilter(
					FilterType.of(type), FilterField.of(field),
					FilterCondition.of(condition), trimmedValue,
				)
			}
			return null
		}
	}
}

/** The item kind a filter keeps, where [ANY] keeps every kind. */
enum class FilterType(val marker: String) {
	ANY(""), WORD("W"), TRANSLATION("T"), CHAPTER("C");

	companion object {
		fun of(marker: String): FilterType = entries.firstOrNull { it.marker == marker } ?: ANY
	}
}

/** What a filter compares, where text and number fields take different conditions. */
enum class FilterField(val marker: String, val numeric: Boolean) {
	NAME("", false),
	DESCRIPTION("D", false),
	SUCCESSES("S", true),
	FAILS("F", true),
	TESTED("N", true),
	RATE("R", true);

	/** The conditions that this field accepts. */
	val conditions: List<FilterCondition>
		get() = if (numeric) NumberConditions else TextConditions

	companion object {
		fun of(marker: String): FilterField = entries.firstOrNull { it.marker == marker } ?: NAME
	}
}

/** The comparison of one filter. */
enum class FilterCondition(val marker: String) {
	CONTAINS("\\"),
	CONTAINS_CASE("c"),
	STARTS("s"),
	ENDS("e"),
	REGEX("r"),
	GREATER(">"),
	LESS("<"),
	EQUALS("=");

	companion object {
		fun of(marker: String): FilterCondition =
			entries.firstOrNull { it.marker == marker } ?: CONTAINS
	}
}

private val OperatorChars = setOf('>', '<', '=')

private val TextConditions = listOf(
	FilterCondition.CONTAINS, FilterCondition.CONTAINS_CASE,
	FilterCondition.STARTS, FilterCondition.ENDS, FilterCondition.REGEX,
)

private val NumberConditions = listOf(
	FilterCondition.GREATER, FilterCondition.LESS, FilterCondition.EQUALS,
)
