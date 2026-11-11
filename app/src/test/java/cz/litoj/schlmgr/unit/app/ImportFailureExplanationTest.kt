package cz.litoj.schlmgr.unit.app

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.ImportFailureExplanation
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListReader
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

	@Test
	fun `the location names the file and its line`() {
		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in explanation("Haus\n"))
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
		val note = placeholder(R.string.import_placeholder_note)
		val translation = placeholder(R.string.import_placeholder_translation)

		assertTrue(fix(R.string.import_fix_separator_swap, "Haus = dům") in explanation("Haus->dům\n"))
		assertTrue(fix(R.string.import_fix_translation_text, "Haus = $translation") in explanation("Haus\n"))
		assertTrue(fix(R.string.import_fix_note_text, "= $note") in explanation("=\n"))
		assertTrue(fix(R.string.import_fix_chapter_close, "}") in explanation("Chapter {\n"))
		assertTrue(fix(R.string.import_fix_description_close, "word[description]") in explanation("word[description"))
	}

	@Test
	fun `a second fix is offered as another way`() {
		val other = text(R.string.import_fix_another, text(R.string.import_fix_escaped_separator, "word;translation\\;extra"))

		assertTrue(other in explanation("word;translation;extra\n"))
	}

	@Test
	fun `a stray symbol gets its diagnosis, never a deletion`() {
		val body = explanation("}\n")

		assertTrue(text(R.string.import_hint_chapter_braces) in body)
		assertTrue(text(R.string.import_hint_no_single_fix) in body)
	}

	@Test
	fun `the marker counts the source characters before the spot`() {
		val content = ImportFailureExplanation.of(parseError("\tword;translation;extra\n"))

		assertEquals('\t'.toString() + " ".repeat("word;translation".length) + "↓", content.pointer)
	}

	@Test
	fun `the copied text carries location source and fix`() {
		val body = explanation("Haus->dům\n")

		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in body)
		assertTrue("Haus->dům" in body)
		assertTrue(fix(R.string.import_fix_separator_swap, "Haus = dům") in body)
		assertFalse("No raw parser reason belongs in the popup: $body", "Reached end of file" in body)
	}

	@Test
	@Config(sdk = [35], qualifiers = "cs")
	fun `the location and guidance use Czech resources`() {
		val body = explanation("Haus\n")

		assertTrue(text(R.string.import_error_location, "Broken.txt", 1) in body)
		assertTrue(fix(R.string.import_fix_translation_text, "Haus = ${placeholder(R.string.import_placeholder_translation)}") in body)
		assertFalse("line 1" in body)
	}
}
