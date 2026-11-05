package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.FilterCondition
import cz.litoj.schlmgr.ui.search.FilterField
import cz.litoj.schlmgr.ui.search.FilterType
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

	@Test
	fun `every written filter reads back as itself`() {
		for (type in FilterType.entries) {
			for (field in FilterField.entries) {
				for (condition in field.conditions) {
					val value = if (field.numeric) "50" else "dog"
					val filter = SearchFilter(type, field, condition, value)
					if (type == FilterType.ANY && field == FilterField.NAME &&
						condition == FilterCondition.CONTAINS
					) {
						assertNull("the first option is plain text", SearchFilter.parse(filter.toQuery()))
					} else assertEquals(filter, SearchFilter.parse(filter.toQuery()))
				}
			}
		}
	}

	@Test
	fun `a text filter writes the explicit condition marker`() {
		assertEquals(
			"\\W\\dog",
			SearchFilter(
				type = FilterType.WORD, field = FilterField.NAME,
				condition = FilterCondition.CONTAINS, value = "dog",
			).toQuery(),
		)
		assertEquals(
			"\\WDsdog",
			SearchFilter(
				type = FilterType.WORD, field = FilterField.DESCRIPTION,
				condition = FilterCondition.STARTS, value = "dog",
			).toQuery(),
		)
	}

	@Test
	fun `a number filter keeps its type source and operator`() {
		assertEquals(
			"\\WR>50",
			SearchFilter(
				type = FilterType.WORD, field = FilterField.RATE,
				condition = FilterCondition.GREATER, value = "50",
			).toQuery(),
		)
	}

	@Test
	fun `a free text query has no filter`() {
		assertNull(SearchFilter.parse("dog"))
	}

	@Test
	fun `a written type and name query reads as a filter`() {
		assertEquals(
			SearchFilter(type = FilterType.CHAPTER, value = "k72"),
			SearchFilter.parse("\\Ck72"),
		)
	}

	@Test
	fun `a parsed filter value trims its edges`() {
		assertEquals(
			SearchFilter(type = FilterType.WORD, field = FilterField.DESCRIPTION, value = "dog"),
			SearchFilter.parse("\\WD dog "),
		)
	}

	@Test
	fun `a prefix without a value has no filter`() {
		assertNull(SearchFilter.parse("\\W"))
		assertNull(SearchFilter.parse("\\"))
	}

	@Test
	fun `a value starting with an operator still searches text`() {
		val item = DbItem(name = "=5", description = "", type = ItemKind.WORD)
		val conditions = listOf(
			FilterCondition.CONTAINS, FilterCondition.CONTAINS_CASE, FilterCondition.STARTS,
			FilterCondition.ENDS, FilterCondition.REGEX,
		)
		for (condition in conditions) {
			val query = SearchFilter(
				field = FilterField.NAME, condition = condition, value = "=5",
			).toQuery()
			val engine = SearchEngine(query)
			assertTrue(query, engine.valid)
			assertTrue(query, engine.correct(item, emptyList()) { _, _ -> })
		}
	}
}
