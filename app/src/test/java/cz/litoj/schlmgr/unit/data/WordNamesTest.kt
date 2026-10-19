package cz.litoj.schlmgr.unit.data

import cz.litoj.schlmgr.data.WordNames
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of the stored-name codecs ([WordNames]): variant expansion
 * (`/` alternatives, `()` groups, escapes), the escaped-field split, and
 * the escape pairs that must not split. The examples come from the
 * original format documentation, so a mismatch means a behavior change.
 */
class WordNamesTest {

	/**
	 * Expected outputs verified against the original engine parser (its javadoc
	 * examples were slightly inaccurate — these are the actual results).
	 */
	@Test
	fun `the documented variant examples parse`() {
		assertEquals(
			listOf("They moved.", "He and she moved."),
			WordNames.parseVariants("They/(He and she) moved.")
		)
		assertEquals(
			listOf("smile.", "I smile.", "You smile."),
			WordNames.parseVariants("/I/You smile.")
		)
		assertEquals(
			listOf("I smile.", "You smile.", "Smile!"),
			WordNames.parseVariants("(I/You smile.)/(Smile!)")
		)
		assertEquals(
			listOf("I 'm here.", "I 'm there.", "I am here.", "I am there."),
			WordNames.parseVariants("I 'm/am here/there.")
		)
		assertEquals(
			listOf("We 're ", "We 're out.", "May you 're ", "May you 're out.", "He's ", "He's out."),
			WordNames.parseVariants("(We/(May you) 're)/He's /out.")
		)
	}

	@Test
	fun `a name without a slash stays a single name`() {
		assertEquals(listOf("plain name"), WordNames.parseVariants("plain name"))
	}

	@Test
	fun `an escaped slash is not a variant`() {
		assertEquals(listOf("a/b c"), WordNames.parseVariants("a\\/b c"))
	}

	@Test
	fun `a trailing backslash drops instead of crashing`() {
		// the parser only runs for names with a `/`. A trailing `\` escapes
		// nothing at the end of the source
		assertEquals(WordNames.parseVariants("a/b"), WordNames.parseVariants("a/b\\"))
	}

	@Test
	fun `split escaped divides the names`() {
		assertEquals(
			listOf("word one", "word two"),
			WordNames.splitEscaped("word one\\word two")
		)
	}

	@Test
	fun `split escaped keeps the variant syntax`() {
		// `\\` and the `/` stay part of one name — no split, input unchanged
		assertEquals(
			listOf("he\\\\/she"),
			WordNames.splitEscaped("he\\\\/she")
		)
	}

	@Test
	fun `split escaped keeps an escaped backslash`() {
		// like the file parser, `\\` is an escape pair — a name can hold a
		// backslash without being cut in two
		assertEquals(
			listOf("a\\\\b"),
			WordNames.splitEscaped("a\\\\b")
		)
	}

	@Test
	fun `split escaped splits after an escaped backslash`() {
		// the escape pair protects only itself — the plain `\` after it still divides
		assertEquals(
			listOf("a\\\\b", "c"),
			WordNames.splitEscaped("a\\\\b\\c")
		)
	}

	@Test
	fun `split escaped keeps a single name whole`() {
		assertEquals(listOf("only"), WordNames.splitEscaped("only"))
	}

	@Test
	fun `an empty group drops nothing`() {
		// `()` carries no alternative — the text after it stays in the
		// variant it belongs to
		assertEquals(listOf("a", "b"), WordNames.parseVariants("a/()b"))
		assertEquals(listOf("a", "b", "c"), WordNames.parseVariants("a/()b/c"))
		// a trailing empty group or separator is not an empty variant
		assertEquals(listOf("a"), WordNames.parseVariants("a/()"))
		assertEquals(listOf("a"), WordNames.parseVariants("a/"))
	}

	@Test
	fun `a stray closing paren is text`() {
		// the unmatched `)` collapses into the pending variant instead of
		// discarding everything after it
		assertEquals(listOf("a d", "b)c d"), WordNames.parseVariants("a/b)c d"))
	}
}
