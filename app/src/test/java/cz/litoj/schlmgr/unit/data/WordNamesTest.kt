package cz.litoj.schlmgr.unit.data

import cz.litoj.schlmgr.data.WordNames
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the examples come from the original format documentation.
 * - a mismatch thus means a behavior change.
 */
class WordNamesTest {

	/**
	 * Expected outputs verified against the original engine parser.
	 * - the javadoc examples there were slightly inaccurate.
	 * - these are the actual results.
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
		// the parser only runs for names with a slash, because a trailing backslash
		// escapes nothing at the end
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
		// an escaped backslash and the slash stay part of one name, so no split happens
		assertEquals(
			listOf("he\\\\/she"),
			WordNames.splitEscaped("he\\\\/she")
		)
	}

	@Test
	fun `split escaped keeps an escaped backslash`() {
		// like the file parser, an escaped backslash is an escape pair, so a name can
		// hold a backslash without a split
		assertEquals(
			listOf("a\\\\b"),
			WordNames.splitEscaped("a\\\\b")
		)
	}

	@Test
	fun `split escaped splits after an escaped backslash`() {
		// the escape pair protects only itself, so the plain backslash after it still divides
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
		// an empty group carries no alternative, so the text after it stays in its
		// variant
		assertEquals(listOf("a", "b"), WordNames.parseVariants("a/()b"))
		assertEquals(listOf("a", "b", "c"), WordNames.parseVariants("a/()b/c"))
		// a trailing empty group or separator is not an empty variant
		assertEquals(listOf("a"), WordNames.parseVariants("a/()"))
		assertEquals(listOf("a"), WordNames.parseVariants("a/"))
	}

	@Test
	fun `a stray closing paren is text`() {
		// the unmatched closing parenthesis collapses into the pending variant instead
		// of discarding everything after it
		assertEquals(listOf("a d", "b)c d"), WordNames.parseVariants("a/b)c d"))
	}
}
