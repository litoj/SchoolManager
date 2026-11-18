package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.wordlist.WordListAutoRepair
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListProblem
import cz.litoj.schlmgr.data.wordlist.WordListRepairKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a suggestion changing one character repairs the import buffer on its own.
 * - anything needing more is left to the popup.
 */
class WordListAutoRepairTest {

	private fun repaired(source: String) = WordListAutoRepair.parse(source, "c")

	private fun failure(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) { WordListAutoRepair.parse(source, "c") }

	private fun applied(source: String): List<Pair<Int, String>> =
		repaired(source).fixes.map { it.lineNumber to it.after }

	@Test
	fun `an unfinished description closes itself`() {
		val fixed = repaired("word;translation[description\n")

		assertEquals(listOf(1 to "word;translation[description]"),
			applied("word;translation[description\n"))
		assertEquals("description", fixed.root.words.single().translations.single().description)
	}

	@Test
	fun `an extra separator becomes the divider it offers`() {
		val fixed = repaired("word;translation;extra\n")

		assertEquals(listOf(1 to "word;translation\\extra"), applied("word;translation;extra\n"))
		assertEquals(listOf("translation", "extra"),
			fixed.root.words.single().translations.map { it.name })
	}

	@Test
	fun `a trailing separator stays as its text when the first offer needs a variant`() {
		val fixed = repaired("word;translation;\n")

		assertEquals(listOf(1 to "word;translation\\;"), applied("word;translation;\n"))
		assertEquals(listOf("translation;"),
			fixed.root.words.single().translations.map { it.name })
	}

	@Test
	fun `a stray symbol closes the chapter around it`() {
		val fixed = repaired("Chapter {\n-\n")

		assertEquals(listOf(2 to "}"), applied("Chapter {\n-\n"))
		assertTrue(fixed.root.children.single().entries.isEmpty())
	}

	@Test
	fun `a fix of a later line follows the fix of the first one`() {
		val source = "word;translation;extra\nnext;t[description\n"
		val fixed = repaired(source)

		assertEquals(listOf(1 to "word;translation\\extra", 2 to "next;t[description]"),
			applied(source))
		assertEquals(listOf("word", "next"), fixed.root.words.map { it.name })
		assertEquals("word;translation;extra", fixed.fixes.first().before)
	}

	@Test
	fun `a fix that needs the text of the user is never applied`() {
		val error = failure("Haus\n")

		assertEquals(WordListProblem.INCOMPLETE_ENTRY, error.problem)
		assertTrue(error.repairs.any { it.kind == WordListRepairKind.TRANSLATION_TEXT })
	}

	@Test
	fun `an arrow asks for the separator no character of it is`() {
		assertEquals(WordListProblem.INCOMPLETE_ENTRY, failure("Haus->dům\n").problem)
		assertEquals(WordListProblem.INCOMPLETE_ENTRY, failure("Haus→dům\n").problem)
	}

	@Test
	fun `a chapter opener gains its brace, the space beside it is no character`() {
		val fixed = repaired("Chapter\n\tword;translation\n}\n")

		assertEquals(listOf(1 to "Chapter {"), applied("Chapter\n\tword;translation\n}\n"))
		assertEquals(listOf("word"), fixed.root.children.single().words.map { it.name })
	}

	@Test
	fun `an unclosed chapter gains no line from the repair loop`() {
		val error = failure("Chapter {\n")

		assertEquals(WordListProblem.CHAPTER_END, error.problem)
	}

	@Test
	fun `an unmatched closer disappears`() {
		val fixed = repaired("w;t\n}\n")

		assertEquals(listOf(2 to ""), applied("w;t\n}\n"))
		assertEquals(listOf("w"), fixed.root.words.map { it.name })
	}

	@Test
	fun `the loop bounds the repairs of one file`() {
		val source = (1..WordListAutoRepair.MAX_AUTO_FIXES + 1)
			.joinToString("\n") { "w$it;t;extra" } + "\n"

		assertEquals(WordListAutoRepair.MAX_AUTO_FIXES + 1, failure(source).lineNumber)
	}
}
