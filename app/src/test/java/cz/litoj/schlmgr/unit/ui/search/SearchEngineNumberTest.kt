package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchIndex
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The number conditions and how an untested row answers them.
 * - an untested rate counts as zero, and the choice keeps such rows out.
 * - the counts have no untested state, zero is their own value.
 * - only a word carries counters, so a container never answers a number.
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
		assertEquals(listOf("untested", "weak", "neverRight", "strong"), matched(rate(FilterOperator.AT_LEAST, 0)))
	}

	@Test
	fun `an untested row answers a rate query as zero`() {
		assertEquals(listOf("untested", "weak", "neverRight"), matched(rate(FilterOperator.LESS, 50)))
		assertEquals(listOf("untested", "weak", "neverRight"), matched(rate(FilterOperator.AT_MOST, 25)))
		assertEquals(listOf("untested", "neverRight"), matched(rate(FilterOperator.AT_MOST, 0)))
		assertEquals(listOf("untested", "neverRight"), matched(rate(FilterOperator.EQUALS, 0)))
		assertEquals(emptyList<String>(), matched(rate(FilterOperator.LESS, 0)))
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

	/** A walked tree: an untested chapter and note beside the words, each word with one counterless translation. */
	private fun walked(query: SearchQuery): List<String> {
		val root = DbItem(id = 1, name = "root", description = "", type = ItemKind.CHAPTER)
		// a container carries summed counters, so a number condition must still skip it
		val nested = DbItem(id = 2, name = "nested", description = "", type = ItemKind.CHAPTER)
		val note = DbItem(id = 3, name = "note", description = "", type = ItemKind.NOTE)
		val translations = rows.map {
			DbItem(id = -it.id, name = "${it.name}-t", description = "", type = ItemKind.TRANSLATION)
		}
		val links = rows.mapIndexed { i, word -> DbLink(parentId = root.id, childId = word.id, position = i) } +
			listOf(
				DbLink(parentId = root.id, childId = nested.id, position = rows.size),
				DbLink(parentId = root.id, childId = note.id, position = rows.size + 1),
			) +
			rows.zip(translations) { word, translation -> DbLink(parentId = word.id, childId = translation.id) }
		val index = SearchIndex(listOf(root, nested, note) + rows + translations, links)
		val hits = mutableListOf<String>()
		SearchEngine(query).search(index, listOf(root)) { item, _, _ -> hits.add(item.name) }
		return hits
	}

	@Test
	fun `a number condition reads the word, never its counterless translations`() {
		assertEquals(listOf("untested"), walked(SearchQuery(criteria = listOf(times(FilterOperator.EQUALS, 0)))))
		assertEquals(listOf("untested", "neverRight"),
			walked(SearchQuery(criteria = listOf(rate(FilterOperator.AT_MOST, 0)))))
	}

	@Test
	fun `a typed translation still finds its word under a number condition`() {
		val query = SearchQuery("strong-t", listOf(times(FilterOperator.AT_LEAST, 1)))
		assertEquals(listOf("strong"), walked(query))
	}

	@Test
	fun `a number condition cannot carry a word in through its translation`() {
		val query = SearchQuery("weak-t", listOf(times(FilterOperator.EQUALS, 0)))
		assertEquals(emptyList<String>(), walked(query))
	}
}
