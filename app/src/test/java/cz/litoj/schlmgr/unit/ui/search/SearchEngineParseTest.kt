package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineParseTest {

	private fun hits(query: String, vararg names: String): List<String> {
		val engine = SearchEngine(query)
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
	fun `an empty query matches everything`() {
		assertEquals(listOf("alpha", "beta"), hits("", "alpha", "beta"))
	}

	@Test
	fun `a plain query is a case-insensitive contains`() {
		assertEquals(listOf("das Haus"), hits("HAUS", "das Haus", "die Schule"))
	}

	@Test
	fun `the search matches each parsed variant of a name`() {
		// the queries are no substrings of the raw name, so only the parsed variant
		// can match, and the display parses the same way
		assertEquals(listOf("(I/You smile.)"), hits("I smile.", "(I/You smile.)", "die Schule"))
		assertEquals(listOf("(I/You smile.)"), hits("You smile.", "(I/You smile.)", "die Schule"))
		assertEquals(listOf("(I/You smile.)"), hits("(I/You smile.)", "(I/You smile.)", "die Schule"))
	}

	@Test
	fun `the starts-with prefix matches name beginnings`() {
		assertEquals(listOf("Schule"), hits("\\sSchule", "Schule", "die Schule"))
	}

	@Test
	fun `the type prefix filters words`() {
		val engine = SearchEngine("\\WHaus")
		val word = DbItem(name = "Haus", description = "", type = ItemKind.WORD)
		val chapter = DbItem(name = "Haus", description = "", type = ItemKind.CHAPTER)
		assertEquals(true, engine.correct(word, emptyList()) { _, _ -> })
		assertEquals(false, engine.correct(chapter, emptyList()) { _, _ -> })
	}

	@Test
	fun `the numeric prefix compares test counts`() {
		val tested = DbItem(
			name = "w", description = "", type = ItemKind.WORD,
			passedTests = 1, failedTests = 1,
		)
		assertEquals(true, SearchEngine("\\WN>1").correct(tested, emptyList()) { _, _ -> })
		assertEquals(false, SearchEngine("\\WN>2").correct(tested, emptyList()) { _, _ -> })
	}

	@Test
	fun `the description prefix matches the description`() {
		val engine = SearchEngine("\\Dnote")
		val item = DbItem(name = "w", description = "a note", type = ItemKind.WORD)
		assertEquals(true, engine.correct(item, emptyList()) { _, _ -> })
	}

	@Test
	fun `a bad regex aborts the run and reports the error`() {
		val engine = SearchEngine("\\r[")
		assertFalse("the run is invalid", engine.valid)
		assertTrue("the failure is reported", engine.regexError?.isNotEmpty() == true)
		val item = DbItem(name = "x", description = "", type = ItemKind.CHAPTER)
		assertEquals(false, engine.correct(item, emptyList()) { _, _ -> })
	}

	@Test
	fun `a numeric operation without a source compares against zero`() {
		val word = DbItem(name = "w", description = "", type = ItemKind.WORD)
		assertEquals("0 > 5 is false", false, SearchEngine("\\W>5").correct(word, emptyList()) { _, _ -> })
		assertEquals("0 < 5 is true", true, SearchEngine("\\W<5").correct(word, emptyList()) { _, _ -> })
		assertEquals("0 = 0 is true", true, SearchEngine("\\W=0").correct(word, emptyList()) { _, _ -> })
	}

	@Test
	fun `numeric sources pair with operations`() {
		val word = DbItem(
			name = "w", description = "", type = ItemKind.WORD,
			passedTests = 2, failedTests = 3,
		)
		assertEquals(true, SearchEngine("\\WS=2").correct(word, emptyList()) { _, _ -> })
		assertEquals(true, SearchEngine("\\WF>2").correct(word, emptyList()) { _, _ -> })
		assertEquals(false, SearchEngine("\\WR>50").correct(word, emptyList()) { _, _ -> })
	}

	@Test
	fun `a description matches only non-empty text and carries the type`() {
		val word = DbItem(name = "w", description = "a note", type = ItemKind.WORD)
		val empty = DbItem(name = "e", description = "", type = ItemKind.WORD)
		val chapter = DbItem(name = "c", description = "a note", type = ItemKind.CHAPTER)
		val engine = SearchEngine("\\WDnote")
		assertEquals(true, engine.correct(word, emptyList()) { _, _ -> })
		assertEquals("an empty description never matches", false, engine.correct(empty, emptyList()) { _, _ -> })
		assertEquals("the type prefix applies to the description search too",
			false, engine.correct(chapter, emptyList()) { _, _ -> })
	}

	@Test
	fun `prefix-only queries match everything of the kind`() {
		val word = DbItem(name = "w", description = "", type = ItemKind.WORD)
		val chapter = DbItem(name = "c", description = "", type = ItemKind.CHAPTER)
		for (query in listOf("\\W", "\\WS")) {
			val engine = SearchEngine(query)
			assertEquals(true, engine.correct(word, emptyList()) { _, _ -> })
			assertEquals(false, engine.correct(chapter, emptyList()) { _, _ -> })
		}
	}
}
