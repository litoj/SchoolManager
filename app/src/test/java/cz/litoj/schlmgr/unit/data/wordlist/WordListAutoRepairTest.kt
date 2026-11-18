package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.wordlist.WordListAutoRepair
import cz.litoj.schlmgr.data.wordlist.WordListChild
import cz.litoj.schlmgr.data.wordlist.WordListChapter
import cz.litoj.schlmgr.data.wordlist.WordListNote
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a suggestion changing one character repairs the import buffer on its own.
 * - anything needing more is left to the popup.
 * - the text of a fix belongs to the parser, see WordListParseFailureTest.
 */
class WordListAutoRepairTest {

	private fun repaired(source: String) = WordListAutoRepair.parse(source, "c")

	private fun failure(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) { WordListAutoRepair.parse(source, "c") }

	/** The tree as prefixed names, the shape the repair left behind. */
	private fun shape(entries: List<WordListChild>): List<String> = entries.flatMap { entry ->
		when (entry) {
			is WordListChapter -> listOf("c:" + entry.name)
			is WordListNote -> listOf("n:" + entry.name)
			is WordListWord -> listOf("w:" + entry.name) + entry.translations.map { "t:" + it.name }
		}
	}

	@Test
	fun `a one-character edit is written back`() {
		val edits = mapOf(
			"word;translation[description\n" to (1 to listOf("w:word", "t:translation")),
			"word;translation;extra\n" to (1 to listOf("w:word", "t:translation", "t:extra")),
			"word;translation;\n" to (1 to listOf("w:word", "t:translation;")),
			"Chapter {\n-\n" to (2 to listOf("c:Chapter")),
			"w;t\n}\n" to (2 to listOf("w:w", "t:t")),
		)

		for ((source, edit) in edits) {
			val fixed = repaired(source)
			assertEquals("$source mended another line", listOf(edit.first), fixed.fixes.map { it.lineNumber })
			assertEquals("$source left another tree", edit.second, shape(fixed.root.entries))
		}
	}

	@Test
	fun `anything else is left to the popup`() {
		val refusals = mapOf(
			"a fix that needs the text of the user" to listOf("Haus\n"),
			"an arrow, which is no separator" to listOf("Haus->dům\n", "Haus→dům\n"),
			"a fix that adds a line of its own" to listOf("Chapter {\n"),
		)

		for ((name, sources) in refusals) {
			for (source in sources) {
				// the parser had offers, the loop wrote none of them back
				assertTrue("$name reached the buffer", failure(source).repairs.isNotEmpty())
			}
		}
	}

	@Test
	fun `a fix of a later line follows the fix of the first one`() {
		val source = "word;translation;extra\nnext;t[description\n"
		val fixed = repaired(source)

		assertEquals(listOf(1, 2), fixed.fixes.map { it.lineNumber })
		assertEquals(listOf("w:word", "t:translation", "t:extra", "w:next", "t:t"),
			shape(fixed.root.entries))
		// the report names the line the way the file has it
		assertEquals("word;translation;extra", fixed.fixes.first().before)
	}

	@Test
	fun `a chapter opener gains its brace, the space beside it is no character`() {
		val fixed = repaired("Chapter\n\tword;translation\n}\n")

		assertEquals(listOf(1), fixed.fixes.map { it.lineNumber })
		assertEquals("the word landed inside the chapter the brace opened",
			listOf("word"), fixed.root.children.single().words.map { it.name })
	}

	@Test
	fun `the loop bounds the repairs of one file`() {
		val source = (1..WordListAutoRepair.MAX_AUTO_FIXES + 1)
			.joinToString("\n") { "w$it;t;extra" } + "\n"

		assertEquals(WordListAutoRepair.MAX_AUTO_FIXES + 1, failure(source).lineNumber)
	}
}
