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
	fun `plain search trims the query and the shown name`() {
		assertEquals(listOf(" das Haus "), hits("  HAUS  ", " das Haus ", "die Schule"))
	}

	@Test
	fun `description search trims the shown description`() {
		val engine = SearchEngine("\\WDnote")
		val item = DbItem(name = "w", description = " note ", type = ItemKind.WORD)
		assertEquals(true, engine.correct(item, emptyList()) { _, _ -> })
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

	@Test
	fun `a number query takes any case and spacing`() {
		val word = DbItem(
			name = "w", description = "", type = ItemKind.WORD,
			passedTests = 2, failedTests = 3,
		)
		val chapter = DbItem(name = "c", description = "", type = ItemKind.CHAPTER)
		for (query in listOf("\\w s = 2", "\\ws=2", "\\W S=2", "\\WS= 2", " \\WS=2 ", "\\ w=0")) {
			val engine = SearchEngine(query)
			assertTrue(query, engine.valid)
			assertEquals(query, true, engine.correct(word, emptyList()) { _, _ -> })
			assertEquals(query, false, engine.correct(chapter, emptyList()) { _, _ -> })
		}
		assertEquals(true, SearchEngine("\\f>2").correct(word, emptyList()) { _, _ -> })
		assertEquals(true, SearchEngine("\\r = 40").correct(word, emptyList()) { _, _ -> })
	}

	@Test
	fun `an unknown letter before an operator is invalid`() {
		for (query in listOf("\\WX=0", "\\X=0", "\\w x > 0", "\\WD=0", "\\WW<1")) assertInvalid(query)
	}

	@Test
	fun `a number operator without a number is invalid`() {
		for (query in listOf("\\WR=abc", "\\R>", "\\W=abc", "\\=x", "\\WR=1.5", "\\WR=99999999999"))
			assertInvalid(query)
	}

	private fun assertInvalid(query: String) {
		val engine = SearchEngine(query)
		val item = DbItem(name = "x", description = "", type = ItemKind.WORD)
		assertFalse(query, engine.valid)
		assertEquals("only a bad regex carries a detail", null, engine.regexError)
		assertEquals(query, false, engine.correct(item, emptyList()) { _, _ -> })
	}

	@Test
	fun `a text operator before an operator is invalid`() {
		for (query in listOf("\\wr=a", "\\Wr=a", "\\w r = a", "\\s=abc", "\\r=a.c", "\\e=5", "\\Wc=5"))
			assertInvalid(query)
	}

	@Test
	fun `text starting with an operator needs the plain or regex form`() {
		assertEquals(listOf("=abc"), hits("\\\\=abc", "=abc", "x"))
		assertEquals(listOf("=abc"), hits("\\C\\=abc", "=abc", "x"))
		assertEquals(listOf("=abc"), hits("\\r[=]a.c", "=abc", "x"))
	}

	@Test
	fun `text markers stay case sensitive`() {
		assertEquals(listOf("Dog"), hits("\\sD", "Dog", "dog"))
		assertEquals(listOf("Dog", "dog"), hits("\\SD", "Dog", "dog"))
	}

	@Test
	fun `every query is trimmed before matching`() {
		assertEquals(listOf("Dog"), hits("  \\sDog  ", "Dog", "x"))
		assertEquals(listOf("a b c", "b"), hits(" b ", "a b c", "b"))
	}
}
