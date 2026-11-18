package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The number conditions and how an untested row answers them.
 * - an untested rate is below every tested one, and the choice keeps such rows out.
 * - the counts have no untested state, zero is their own value.
 */
class SearchEngineNumberTest {

	private fun row(name: String, passed: Int, failed: Int) = DbItem(
		id = name.hashCode(), name = name, description = "", type = ItemKind.WORD,
		passedTests = passed, failedTests = failed,
	)

	private val rows = listOf(
		row("untested", 0, 0),
		row("weak", 1, 3),
		row("neverRight", 0, 2),
		row("strong", 8, 2),
	)

	private fun rate(operator: FilterOperator, percent: Int, untested: UntestedRows = UntestedRows.INCLUDE) =
		Criterion(SearchTag.RATE, operator, CriterionValue.Rate(percent, untested))

	private fun times(operator: FilterOperator, count: Int) =
		Criterion(SearchTag.TESTED, operator, CriterionValue.Times(count))

	/** The rows a query reports, in list order. */
	private fun matched(vararg criteria: Criterion): List<String> {
		val engine = SearchEngine(SearchQuery(criteria = criteria.toList()))
		val hits = mutableListOf<String>()
		for (item in rows) if (engine.correct(item, emptyList()) { _, _ -> }) hits.add(item.name)
		return hits
	}

	@Test
	fun `at least and at most include their own bound`() {
		assertEquals(listOf("weak", "strong"), matched(rate(FilterOperator.AT_LEAST, 25)))
		assertEquals(listOf("strong"), matched(rate(FilterOperator.GREATER, 25)))
		assertEquals(listOf("weak", "neverRight", "strong"), matched(rate(FilterOperator.AT_LEAST, 0)))
	}

	@Test
	fun `an untested row answers a rate query as the lowest rate`() {
		assertEquals(listOf("untested", "weak", "neverRight"), matched(rate(FilterOperator.LESS, 50)))
		assertEquals(listOf("untested", "weak", "neverRight"), matched(rate(FilterOperator.AT_MOST, 25)))
		assertEquals(listOf("untested", "neverRight"), matched(rate(FilterOperator.AT_MOST, 0)))
		assertEquals(listOf("neverRight"), matched(rate(FilterOperator.EQUALS, 0)))
	}

	@Test
	fun `the choice keeps the untested rows out of a rate query`() {
		assertEquals(listOf("weak", "neverRight"), matched(rate(FilterOperator.LESS, 50, UntestedRows.EXCLUDE)))
		assertEquals(listOf("weak", "strong"), matched(rate(FilterOperator.AT_LEAST, 25, UntestedRows.EXCLUDE)))
	}

	@Test
	fun `a count query reads zero as its own value`() {
		assertEquals(listOf("untested"), matched(times(FilterOperator.EQUALS, 0)))
		assertEquals(listOf("untested", "weak", "neverRight"), matched(times(FilterOperator.LESS, 10)))
		assertEquals(listOf("strong"), matched(times(FilterOperator.AT_LEAST, 10)))
	}

	@Test
	fun `two number conditions narrow one run`() {
		val bound = listOf(times(FilterOperator.AT_LEAST, 2), rate(FilterOperator.LESS, 50))
		assertEquals(listOf("weak", "neverRight"), matched(*bound.toTypedArray()))
	}
}
