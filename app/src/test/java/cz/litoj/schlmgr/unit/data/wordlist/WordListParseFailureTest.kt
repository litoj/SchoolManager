package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListPlaceholder
import cz.litoj.schlmgr.data.wordlist.WordListProblem
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.data.wordlist.WordListRepair
import cz.litoj.schlmgr.data.wordlist.WordListRepairKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a repair is a single edit the parser confirmed by re-parsing the file.
 * - [WordListRepair.MISSING_TEXT] stands for text only the user can supply.
 * - every failure names its problem and the place of it.
 * - every failure offers its edits, or none at all.
 * - no edit throws away text the file already has.
 */
class WordListParseFailureTest {

	private val mark = WordListRepair.MISSING_TEXT.toString()

	/** Where a failure stops: its line, its problem, its spot inside the line. */
	private class Place(val lineNumber: Int, val problem: WordListProblem, val problemIndex: Int)

	/** One confirmed edit: its kind, the line it writes, the open spot it leaves. */
	private class Edit(
		val kind: WordListRepairKind,
		val text: String,
		val placeholder: WordListPlaceholder?,
	)

	private fun failure(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) {
			WordListReader.parse(source, "c")
		}

	@Test
	fun `every failure names its problem and its place`() {
		val places = mapOf(
			"Haus\n" to Place(1, WordListProblem.INCOMPLETE_ENTRY, 4),
			"word; [description]\n" to Place(1, WordListProblem.TRANSLATION_BEFORE_DESCRIPTION, 6),
			"word\\ [d]=trl\n" to Place(1, WordListProblem.EMPTY_VARIANT, 4),
			"word;translation;extra\n" to Place(1, WordListProblem.EXTRA_SEPARATOR, 16),
			"=\n" to Place(1, WordListProblem.MISSING_NOTE_TEXT, 1),
			"=named\n" to Place(1, WordListProblem.NOTE_WITHOUT_DESCRIPTION, 0),
			"ch {\n=named\n}\n" to Place(2, WordListProblem.NOTE_WITHOUT_DESCRIPTION, 0),
			"=named=again\n" to Place(1, WordListProblem.NOTE_SECOND_SEPARATOR, 6),
			"a\\b {\nword;t\n}\n" to Place(1, WordListProblem.CHAPTER_DIVIDER, 1),
			"}\nword;t\n" to Place(1, WordListProblem.CHAPTER_BRACES, 0),
			"Chapter {\n" to Place(1, WordListProblem.CHAPTER_END, 8),
			"-\n" to Place(1, WordListProblem.SYMBOL_ONLY, 0),
		)

		for ((source, place) in places) {
			val error = failure(source)
			assertEquals("wrong line for $source", place.lineNumber, error.lineNumber)
			assertEquals("wrong problem for $source", place.problem, error.problem)
			assertEquals("wrong place for $source", place.problemIndex, error.problemIndex)
		}
	}

	@Test
	fun `every failure offers the edit of its kind`() {
		val edits = mapOf(
			"Haus\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_TEXT, "Haus = $mark", WordListPlaceholder.TRANSLATION)),
			"Haus->dům\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_TEXT, "Haus->dům = $mark", WordListPlaceholder.TRANSLATION)),
			"Haus→dům\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_TEXT, "Haus→dům = $mark", WordListPlaceholder.TRANSLATION)),
			"pes;\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_TEXT, "pes; $mark", WordListPlaceholder.TRANSLATION)),
			"= [description]\n" to listOf(
				Edit(WordListRepairKind.NOTE_TEXT, "= $mark [description]", WordListPlaceholder.NOTE)),
			"[description]\n" to listOf(
				Edit(WordListRepairKind.NOTE_TEXT, "= $mark [description]", WordListPlaceholder.NOTE)),
			"word; [description]\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_ORDER, "word; $mark [description]",
					WordListPlaceholder.TRANSLATION)),
			"=named\n" to listOf(
				Edit(WordListRepairKind.WORD_BEFORE_TRANSLATION, "$mark =named", WordListPlaceholder.WORD)),
			"word;translation;extra\n" to listOf(
				Edit(WordListRepairKind.VARIANT_DIVIDER, "word;translation\\extra", null),
				Edit(WordListRepairKind.ESCAPED_SEPARATOR, "word;translation\\;extra", null)),
			"word;translation;\n" to listOf(
				Edit(WordListRepairKind.ESCAPED_SEPARATOR, "word;translation\\;", null)),
			"=named=again\n" to listOf(
				Edit(WordListRepairKind.ESCAPED_SEPARATOR, "$mark =named\\=again", WordListPlaceholder.WORD)),
			"=named=\n" to listOf(
				Edit(WordListRepairKind.ESCAPED_SEPARATOR, "$mark =named\\=", WordListPlaceholder.WORD)),
			"=note\\oops\n" to listOf(
				Edit(WordListRepairKind.DOUBLED_BACKSLASH, "$mark =note\\\\oops", WordListPlaceholder.WORD)),
			"word\\ [d]=trl\n" to listOf(
				Edit(WordListRepairKind.VARIANT_TEXT, "word\\$mark [d]=trl", WordListPlaceholder.VARIANT)),
			"word[description" to listOf(
				Edit(WordListRepairKind.DESCRIPTION_CLOSE, "word[description]", null)),
			"Chapter {\n" to listOf(Edit(WordListRepairKind.CHAPTER_CLOSE, "}", null)),
			"Chapter {\n-\n" to listOf(Edit(WordListRepairKind.CHAPTER_CLOSE, "}", null)),
			"Chapter\n\tword;translation\n" to listOf(
				Edit(WordListRepairKind.CHAPTER_BLOCK, "Chapter {\n…\n}", null)),
			"Chapter\n\tword;translation\n}\n" to listOf(
				Edit(WordListRepairKind.CHAPTER_OPEN, "Chapter {", null)),
			// the repair of one line stays offered while another line still fails
			"Chapter {\npes\n" to listOf(
				Edit(WordListRepairKind.TRANSLATION_TEXT, "pes = $mark", WordListPlaceholder.TRANSLATION)),
			"{\n" to listOf(
				Edit(WordListRepairKind.CHAPTER_NAMED_BLOCK, "$mark {\n…\n}", WordListPlaceholder.CHAPTER)),
			"]\n" to listOf(Edit(WordListRepairKind.EXTRA_CHAR, "", null)),
			"}\n" to listOf(Edit(WordListRepairKind.EXTRA_CHAR, "", null)),
		)

		for ((source, offered) in edits) {
			val found = failure(source).repairs
			for (edit in offered) {
				val match = found.firstOrNull { it.kind == edit.kind }
				assertTrue("$source offers no ${edit.kind} in $found", match != null)
				assertEquals("$source edits more than its own text", edit.text, match!!.text)
				assertEquals("$source leaves the wrong kind open", edit.placeholder, match.placeholder)
			}
		}
	}

	@Test
	fun `no fix does what its author must decide`() {
		val refusals = mapOf(
			"a symbol that is no entry" to "-\n",
			"a line of nothing but a separator" to "=\n",
			"a name with two forms" to "name\\variant {\nword;translation\n}\n",
			"a brace the file already closed" to "Chapter {\n-\n}\n",
		)

		for ((name, source) in refusals) {
			assertTrue("$name offered a fix", failure(source).repairs.isEmpty())
		}

		// the extra separator becomes a divider or text, never one welded word
		assertTrue("a fix welded two words together",
			failure("word;translation;extra\n").repairs.none { it.text == "word;translationextra" })
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
}
