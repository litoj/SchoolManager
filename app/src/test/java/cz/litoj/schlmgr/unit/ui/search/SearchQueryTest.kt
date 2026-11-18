package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The query the field and the filter editor build together.
 * - a tag owns one criterion, so adding one again replaces it.
 */
class SearchQueryTest {

	private val rate = Criterion(SearchTag.RATE, FilterOperator.AT_MOST, CriterionValue.Rate(50))

	private val words = Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.WORD))

	@Test
	fun `an empty query searches nothing`() {
		assertTrue(SearchQuery().isEmpty)
		assertTrue(SearchQuery(text = "   ").isEmpty)
		assertFalse(SearchQuery(text = "a").isEmpty)
		assertFalse(SearchQuery(criteria = listOf(rate)).isEmpty)
	}

	@Test
	fun `a tag keeps one criterion only`() {
		val low = Criterion(SearchTag.RATE, FilterOperator.LESS, CriterionValue.Rate(10))
		val query = SearchQuery(text = "dům").with(rate).with(low)
		assertEquals(listOf(low), query.criteria)
		assertEquals("dům", query.text)
	}

	@Test
	fun `the other tags keep their criterion`() {
		val query = SearchQuery().with(words).with(rate)
		assertEquals(listOf(words, rate), query.criteria)
		assertEquals(rate, query.criterionOf(SearchTag.RATE))
		assertNull(query.criterionOf(SearchTag.NAME))
		assertEquals(listOf(words), query.without(SearchTag.RATE).criteria)
	}

	@Test
	fun `every tag offers the comparisons of its value`() {
		assertEquals(listOf(FilterOperator.IS), SearchTag.TYPE.operators)
		assertEquals(
			listOf(
				FilterOperator.GREATER, FilterOperator.AT_LEAST,
				FilterOperator.LESS, FilterOperator.AT_MOST, FilterOperator.EQUALS,
			),
			SearchTag.RATE.operators,
		)
		assertFalse(FilterOperator.CONTAINS in SearchTag.TESTED.operators)
		assertTrue(FilterOperator.REGEX in SearchTag.DESCRIPTION.operators)
	}

	@Test
	fun `only a rate carries the rule for the untested rows`() {
		val out = Criterion(SearchTag.RATE, FilterOperator.LESS, CriterionValue.Rate(50, UntestedRows.EXCLUDE))
		assertEquals(UntestedRows.EXCLUDE, (out.value as CriterionValue.Rate).untested)
		assertEquals(
			UntestedRows.INCLUDE,
			(Criterion(SearchTag.RATE, FilterOperator.LESS, CriterionValue.Rate(50)).value as CriterionValue.Rate).untested,
		)
	}
}
