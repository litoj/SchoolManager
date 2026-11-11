package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListPlaceholder
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.data.wordlist.WordListRepair
import cz.litoj.schlmgr.data.wordlist.WordListRepairKind
import cz.litoj.schlmgr.data.wordlist.WordListProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a repair is a single edit the parser confirmed by re-parsing the file.
 * - [WordListRepair.MISSING_TEXT] stands for text only the user can supply.
 */
class WordListRepairTest {

	private val mark = WordListRepair.MISSING_TEXT.toString()

	private fun failure(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) {
			WordListReader.parse(source, "c")
		}

	private fun forms(source: String): List<String> = failure(source).repairs.map { it.text }

	private fun expectFix(source: String, form: String) {
		val found = forms(source)
		assertTrue("Expected $form in $found", form in found)
	}

	@Test
	fun `the arrow becomes the separator this file already uses`() {
		expectFix("Haus->dům\n", "Haus = dům")
		expectFix("first;ok\nHaus->dům\n", "Haus ; dům")
	}

	@Test
	fun `a fix keeps the text the file already has`() {
		expectFix("Haus\n", "Haus = $mark")
		expectFix("pes;\n", "pes; $mark")
	}

	@Test
	fun `a note gets its text after the separator that opens it`() {
		expectFix("=\n", "= $mark")
		expectFix("= [description]\n", "= $mark [description]")
	}

	@Test
	fun `a literal separator in a note keeps itself and gains an escape`() {
		expectFix("=named=again\n", "=named\\=again")
		expectFix("=named=\n", "=named\\=")
	}

	@Test
	fun `a backslash in a note doubles`() {
		expectFix("=note\\oops\n", "=note\\\\oops")
	}

	@Test
	fun `an extra separator becomes the divider of the next translation`() {
		expectFix("word;translation;extra\n", "word;translation\\extra")
	}

	@Test
	fun `a description gets the translation it opened before`() {
		expectFix("word; [description]\n", "word; $mark [description]")
		expectFix("[description]\n", "= $mark [description]")
	}

	@Test
	fun `an unfinished description closes`() {
		expectFix("word[description", "word[description]")
	}

	@Test
	fun `an empty variant fills itself`() {
		expectFix("word\\ [d]=trl\n", "word\\$mark [d]=trl")
	}

	@Test
	fun `an indented block opens its chapter`() {
		expectFix("Chapter\n\tword;translation\n}\n", "Chapter {")
	}

	@Test
	fun `a block fix shows the closing brace it adds too`() {
		expectFix("Chapter\n\tword;translation\n", "Chapter {\n…\n}")
		expectFix("{\n", "$mark {\n…\n}")
	}

	@Test
	fun `an unclosed chapter gains its closing brace`() {
		expectFix("Chapter {\n", "}")
		expectFix("Chapter {\nword;translation\n", "}")
	}

	@Test
	fun `a stray symbol becomes the brace its position needs`() {
		expectFix("Chapter {\n-\n", "}")
	}

	@Test
	fun `a symbol that is no entry gets no fix, only its diagnosis`() {
		for (source in listOf("]\n", "}\n", "-\n")) assertTrue("$source offered a fix", failure(source).repairs.isEmpty())
	}

	@Test
	fun `no fix throws away text the file already has`() {
		val sources = listOf(
			"Haus\n", "pes;\n", "Haus->dům\n", "=\n", "=named=again\n", "=named=\n", "=note\\oops\n",
			"word;translation;extra\n", "word;translation;\n", "word\\ [d]=trl\n", "word[description",
			"word; [description]\n", "[description]\n", "{\n", "Chapter {\n", "]\n", "-\n",
		)

		for (source in sources) {
			val error = failure(source)
			val kept = error.sourceLine.count { it.isLetterOrDigit() }
			for (repair in error.repairs) {
				// the closing brace is its own line, not a rewrite of the failing one
				if (repair.kind == WordListRepairKind.CHAPTER_CLOSE) continue
				val inFix = repair.text.count { it.isLetterOrDigit() }
				assertTrue("Fix ${repair.text} lost text of $source", inFix >= kept)
			}
		}
	}

	@Test
	fun `a name with two forms is left to its author`() {
		assertTrue(failure("name\\variant {\nword;translation\n}\n").repairs.isEmpty())
	}

	@Test
	fun `a fix keeps a separator the file has, as text or as a divider`() {
		expectFix("word;translation;\n", "word;translation\\;")
		expectFix("word;translation;extra\n", "word;translation\\extra")
	}

	@Test
	fun `no fix welds two words together`() {
		assertTrue(failure("word;translation;extra\n").repairs.none { it.text == "word;translationextra" })
	}

	@Test
	fun `no fix closes a brace the file already closed`() {
		assertTrue(failure("Chapter {\n-\n}\n").repairs.none { it.text == "}" })
	}

	@Test
	fun `a fix leaves the error of another line alone`() {
		expectFix("Chapter {\npes\n", "pes = $mark")
	}

	@Test
	fun `a repair names the kind of text it leaves open`() {
		val kinds = mapOf(
			"Haus;\n" to WordListPlaceholder.TRANSLATION,
			"=\n" to WordListPlaceholder.NOTE,
			"{\n" to WordListPlaceholder.CHAPTER,
			"word\\ [d]=trl\n" to WordListPlaceholder.VARIANT,
		)

		for ((source, kind) in kinds) {
			val repairs = failure(source).repairs.filter { mark in it.text }
			assertTrue("Expected a marked form in $source", repairs.isNotEmpty())
			assertTrue("Expected $kind in $source", repairs.all { it.placeholder == kind })
		}
	}

	@Test
	fun `every fix names the edit it offers`() {
		val edits = mapOf(
			"Haus->dům\n" to WordListRepairKind.SEPARATOR_SWAP,
			"Haus\n" to WordListRepairKind.TRANSLATION_TEXT,
			"pes;\n" to WordListRepairKind.TRANSLATION_TEXT,
			"=\n" to WordListRepairKind.NOTE_TEXT,
			"word; [description]\n" to WordListRepairKind.TRANSLATION_ORDER,
			"word;translation;extra\n" to WordListRepairKind.VARIANT_DIVIDER,
			"word;translation;\n" to WordListRepairKind.ESCAPED_SEPARATOR,
			"=named=again\n" to WordListRepairKind.ESCAPED_SEPARATOR,
			"=note\\oops\n" to WordListRepairKind.DOUBLED_BACKSLASH,
			"word\\ [d]=trl\n" to WordListRepairKind.VARIANT_TEXT,
			"word[description" to WordListRepairKind.DESCRIPTION_CLOSE,
			"Chapter {\n" to WordListRepairKind.CHAPTER_CLOSE,
			"Chapter\n\tword;translation\n" to WordListRepairKind.CHAPTER_BLOCK,
			"{\n" to WordListRepairKind.CHAPTER_NAMED_BLOCK,
		)

		for ((source, kind) in edits) {
			val found = failure(source).repairs.map { it.kind }
			assertTrue("Expected $kind in $found for $source", kind in found)
		}
	}

	@Test
	fun `the failure keeps its place while offering a fix`() {
		val error = failure("word;translation;extra\n")

		assertEquals(WordListProblem.EXTRA_SEPARATOR, error.problem)
		assertEquals(error.sourceLine.indexOf("extra") - 1, error.problemIndex)
	}
}
