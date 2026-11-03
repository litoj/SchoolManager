package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the fold strips the marks of both sides, so a plain query finds the marked text.
 * - the case rules of each finder stay as they are.
 * - the regex pattern stays as written, since it is the user's own text.
 */
class DiacriticSearchTest {

	private fun hits(query: String, fold: Boolean, vararg names: String): List<String> {
		val engine = SearchEngine(query, fold)
		val matched = ArrayList<String>()
		for ((i, name) in names.withIndex()) {
			// distinct ids, because the engine dedups emitted hits by row id
			engine.correct(DbItem(id = i + 1, name = name, description = "", type = ItemKind.CHAPTER), emptyList()) { bd, _ ->
				matched.add(bd.name)
			}
		}
		return matched
	}

	@Test
	fun `a plain query finds the marked names only under the fold`() {
		assertEquals("the fold off keeps the exact marks", emptyList<String>(), hits("sidlo", false, "sídlo"))
		assertEquals(listOf("sídlo"), hits("sidlo", true, "sídlo", "byt"))
	}

	@Test
	fun `a marked query finds the plain names under the fold`() {
		assertEquals(listOf("sidlo"), hits("sídlo", true, "sidlo", "byt"))
	}

	@Test
	fun `the fold keeps the case-insensitive contains`() {
		assertEquals(listOf("sídlo"), hits("SIDLO", true, "sídlo", "byt"))
	}

	@Test
	fun `the starts-with prefix folds too`() {
		assertEquals(listOf("sídlo"), hits("\\ssid", true, "sídlo", "byt sídlo"))
	}

	@Test
	fun `the parsed variants fold like the raw name`() {
		assertEquals(listOf("(já/ty)"), hits("ja", true, "(já/ty)", "byt"))
	}

	@Test
	fun `the description search folds too`() {
		val item = DbItem(name = "w", description = "význam slova", type = ItemKind.WORD)
		assertTrue(SearchEngine("\\Dvyznam", true).correct(item, emptyList()) { _, _ -> })
		assertFalse(SearchEngine("\\Dvyznam", false).correct(item, emptyList()) { _, _ -> })
	}

	@Test
	fun `the regex pattern keeps its marks under the fold`() {
		val marked = DbItem(name = "sídlo", description = "", type = ItemKind.CHAPTER)
		val plain = DbItem(name = "sidlo", description = "", type = ItemKind.CHAPTER)
		assertTrue("the pattern matches the marked name as written",
			SearchEngine("\\rsídlo", true).correct(marked, emptyList()) { _, _ -> })
		assertFalse("the fold leaves the pattern and the name alone, so the plain name misses",
			SearchEngine("\\rsídlo", true).correct(plain, emptyList()) { _, _ -> })
	}
}
