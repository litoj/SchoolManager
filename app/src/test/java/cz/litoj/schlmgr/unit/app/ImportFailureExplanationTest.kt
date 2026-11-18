package cz.litoj.schlmgr.unit.app

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.ImportFailureExplanation
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListPlaceholder
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.data.wordlist.WordListRepair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The popup content of a word-list failure, apart from the popup itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportFailureExplanationTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	private fun parseError(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) {
			WordListReader.parse(source, "Broken.txt")
		}

	private fun explanation(source: String): String = ImportFailureExplanation.of(parseError(source)).text

	private fun text(id: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(id, *args)

	/** A fix is one full sentence naming the reason and the line it produces. */
	private fun fix(sentence: Int, form: String = ""): String =
		if (form.isEmpty()) text(sentence) else text(sentence, form)

	private fun placeholder(id: Int): String = "<${text(id)}>"

	/** The line a fix asks for, its open spot shown by the placeholder of its kind. */
	private fun form(repair: WordListRepair): String {
		val id = when (repair.placeholder) {
			WordListPlaceholder.CHAPTER -> R.string.import_placeholder_chapter
			WordListPlaceholder.TRANSLATION -> R.string.import_placeholder_translation
			WordListPlaceholder.NOTE -> R.string.import_placeholder_note
			WordListPlaceholder.VARIANT -> R.string.import_placeholder_variant
			WordListPlaceholder.WORD -> R.string.import_placeholder_word
			else -> R.string.import_placeholder_text
		}
		return repair.text.replace(WordListRepair.MISSING_TEXT.toString(), placeholder(id))
	}

	@Test
	fun `a fix is the whole message, only a stuck line explains itself`() {
		val fixed = explanation("Haus\n")

		assertFalse(text(R.string.import_hint_incomplete_entry) in fixed)
		assertFalse(text(R.string.import_hint_no_single_fix) in fixed)

		val stuck = explanation("[[description]]\n")

		assertTrue(text(R.string.import_hint_description_without_text) in stuck)
		assertTrue(text(R.string.import_hint_no_single_fix) in stuck)
	}

	@Test
	fun `every fix sentence names the line it asks for`() {
		val sentences = mapOf(
			"Haus\n" to R.string.import_fix_translation_text,
			"= [description]\n" to R.string.import_fix_note_text,
			"=named\n" to R.string.import_fix_word_before_translation,
			"word; [description]\n" to R.string.import_fix_translation_order,
			"word[description" to R.string.import_fix_description_close,
			"Chapter {\n" to R.string.import_fix_chapter_close,
			"}\n" to R.string.import_fix_extra_char,
		)

		for ((source, sentence) in sentences) {
			val error = parseError(source)
			val body = ImportFailureExplanation.of(error).text
			// the sentence carries the very line the parser confirmed
			val named = error.repairs.any { fix(sentence, form(it)) in body }
			assertTrue("$source offers no line its sentence names: $body", named)
		}
	}

	@Test
	fun `a second fix is offered as another way`() {
		val other = text(R.string.import_fix_another, text(R.string.import_fix_escaped_separator, "word;translation\\;extra"))

		assertTrue(other in explanation("word;translation;extra\n"))
	}

	@Test
	fun `the marker counts the source characters before the spot`() {
		val content = ImportFailureExplanation.of(parseError("\tword;translation;extra\n"))

		assertEquals('\t'.toString() + " ".repeat("word;translation".length) + "↓", content.pointer)
	}

	@Test
	fun `the copied text carries location source and fix`() {
		val body = explanation("word;translation;extra\n")

		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in body)
		assertTrue("word;translation;extra" in body)
		assertTrue(fix(R.string.import_fix_variant_divider, "word;translation\\extra") in body)
		assertFalse("No raw parser reason belongs in the popup: $body", "Reached end of file" in body)

		// a line with no fix keeps its location and carries its diagnosis instead
		val stuck = explanation("-\n")

		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in stuck)
		assertTrue(text(R.string.import_hint_symbol_only) in stuck)
		assertTrue(text(R.string.import_hint_no_single_fix) in stuck)
		assertFalse(fix(R.string.import_fix_extra_char) in stuck)
	}

	@Test
	@Config(sdk = [35], qualifiers = "cs")
	fun `the location and guidance use Czech resources`() {
		val error = parseError("Haus\n")
		val body = ImportFailureExplanation.of(error).text

		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in body)
		assertTrue(fix(R.string.import_fix_translation_text, form(error.repairs.first())) in body)
		assertFalse("line 1" in body)
	}

	@Test
	fun `the editor opens on the failing character of its line`() {
		val first = "Haus\nWasser;water\n"
		val later = "Wasser;water\nHaus\n"

		assertEquals(4, ImportFailureExplanation.caretIn(first, parseError(first)))
		assertEquals(17, ImportFailureExplanation.caretIn(later, parseError(later)))
	}
}
