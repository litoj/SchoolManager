package cz.litoj.schlmgr.ui.explorer.search

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of [SearchEngine]'s query parsing — the grammar is unchanged legacy
 * behaviour, so these pin the port against it. The empty-query case is a
 * regression test: it crashed with `StringIndexOutOfBoundsException` before.
 */
class SearchEngineParseTest {

	/** Runs [query] over one item per name and returns the matching names, in order. */
	private fun hits(query: String, vararg names: String): List<String> {
		val engine = SearchEngine(query)
		val matched = ArrayList<String>()
		for ((i, name) in names.withIndex()) {
			// distinct ids: the engine dedups emitted hits by row id
			engine.correct(DbItem(id = i + 1, name = name, description = "", type = ItemKind.CHAPTER), emptyList()) { bd, _ ->
				matched.add(bd.name)
			}
		}
		return matched
	}

	@Test
	fun emptyQueryMatchesEverything() {
		assertEquals(listOf("alpha", "beta"), hits("", "alpha", "beta"))
	}

	@Test
	fun plainQueryIsCaseInsensitiveContains() {
		assertEquals(listOf("das Haus"), hits("HAUS", "das Haus", "die Schule"))
	}

	@Test
	fun startsWithPrefix() {
		assertEquals(listOf("Schule"), hits("\\sSchule", "Schule", "die Schule"))
	}

	@Test
	fun typePrefixFiltersWords() {
		val engine = SearchEngine("\\WHaus")
		val word = DbItem(name = "Haus", description = "", type = ItemKind.WORD)
		val chapter = DbItem(name = "Haus", description = "", type = ItemKind.CHAPTER)
		assertEquals(true, engine.correct(word, emptyList()) { _, _ -> })
		assertEquals(false, engine.correct(chapter, emptyList()) { _, _ -> })
	}

	@Test
	fun numericPrefixComparesTestCounts() {
		val tested = DbItem(
			name = "w", description = "", type = ItemKind.WORD,
			passedTests = 1, failedTests = 1,
		)
		assertEquals(true, SearchEngine("\\WN>1").correct(tested, emptyList()) { _, _ -> })
		assertEquals(false, SearchEngine("\\WN>2").correct(tested, emptyList()) { _, _ -> })
	}

	@Test
	fun descriptionPrefixMatchesDescription() {
		val engine = SearchEngine("\\Dnote")
		val item = DbItem(name = "w", description = "a note", type = ItemKind.WORD)
		assertEquals(true, engine.correct(item, emptyList()) { _, _ -> })
	}
}
