package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the fold strips the marks of both sides, so a plain query finds the marked text.
 * - the case rules of each finder stay as they are.
 * - the pattern stays as written, since it is the user's own text.
 */
class DiacriticSearchTest {

	private fun hits(query: SearchQuery, fold: Boolean, vararg names: String): List<String> {
		val engine = SearchEngine(query, fold)
		val matched = ArrayList<String>()
		for ((i, name) in names.withIndex()) {
			// distinct ids, because the engine dedups emitted hits by row id
			engine.correct(
				DbItem(id = i + 1, name = name, description = "", type = ItemKind.CHAPTER),
				emptyList(),
			) { item, _ -> matched.add(item.name) }
		}
		return matched
	}

	private fun text(value: String) = SearchQuery(text = value)

	private fun criterion(tag: SearchTag, operator: FilterOperator, value: String) =
		SearchQuery(criteria = listOf(Criterion(tag, operator, CriterionValue.Text(value))))

	@Test
	fun `a plain query finds the marked names only under the fold`() {
		assertEquals("the fold off keeps the exact marks", emptyList<String>(), hits(text("sidlo"), false, "sídlo"))
		assertEquals(listOf("sídlo"), hits(text("sidlo"), true, "sídlo", "byt"))
	}

	@Test
	fun `a marked query finds the plain names under the fold`() {
		assertEquals(listOf("sidlo"), hits(text("sídlo"), true, "sidlo", "byt"))
	}

	@Test
	fun `the fold keeps the case-insensitive contains`() {
		assertEquals(listOf("sídlo"), hits(text("SIDLO"), true, "sídlo", "byt"))
	}

	@Test
	fun `a starts-with criterion folds too`() {
		val starts = criterion(SearchTag.NAME, FilterOperator.STARTS, "sid")
		assertEquals(listOf("sídlo"), hits(starts, true, "sídlo", "byt sídlo"))
	}

	@Test
	fun `the parsed variants fold like the raw name`() {
		assertEquals(listOf("(já/ty)"), hits(text("ja"), true, "(já/ty)", "byt"))
	}

	@Test
	fun `the description search folds too`() {
		val item = DbItem(name = "w", description = "význam slova", type = ItemKind.WORD)
		val note = criterion(SearchTag.DESCRIPTION, FilterOperator.CONTAINS, "vyznam")
		assertTrue(SearchEngine(note, true).correct(item, emptyList()) { _, _ -> })
		assertFalse(SearchEngine(note, false).correct(item, emptyList()) { _, _ -> })
	}

	@Test
	fun `the pattern keeps its marks under the fold`() {
		val marked = DbItem(name = "sídlo", description = "", type = ItemKind.CHAPTER)
		val plain = DbItem(name = "sidlo", description = "", type = ItemKind.CHAPTER)
		val pattern = criterion(SearchTag.NAME, FilterOperator.REGEX, "sídlo")
		assertTrue("the pattern matches the marked name as written",
			SearchEngine(pattern, true).correct(marked, emptyList()) { _, _ -> })
		assertFalse("the fold leaves the pattern and the name alone, so the plain name misses",
			SearchEngine(pattern, true).correct(plain, emptyList()) { _, _ -> })
	}
}
