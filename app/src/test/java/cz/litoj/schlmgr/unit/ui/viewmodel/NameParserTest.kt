package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The display form of a variant-syntax name through [ItemRow.nameParser].
 * - the name parses only when it carries variant syntax an escaped reader meets.
 * - that syntax is an escaped slash or a closing parenthesis with a slash.
 * - the variants join on one line while short.
 * - a variant longer than 18 characters goes on its own line.
 */
class NameParserTest {

	private val savedParse = ItemRow.parse

	@After
	fun restoreParse() {
		ItemRow.parse = savedParse
	}

	@Test
	fun `short variants join on one line with a backslash`() {
		assertEquals("They moved.\\He and she moved.", ItemRow.nameParser("They/(He and she) moved."))
	}

	@Test
	fun `a variant longer than 18 characters goes on its own line`() {
		assertEquals(
			"They moved.\na genuinely very long alternative moved.",
			ItemRow.nameParser("They/(a genuinely very long alternative) moved."))
	}

	@Test
	fun `the separator is chosen per variant`() {
		// the long first variant gets no separator, the short second a backslash
		assertEquals(
			"a much longer first alternative here\\second",
			ItemRow.nameParser("(a much longer first alternative here)/second"))
	}

	@Test
	fun `a name whose only variant collapses stays single`() {
		// a stray closing parenthesis is text now, and the single variant keeps it
		assertEquals("word/syn(onym)", ItemRow.nameParser("word\\/syn\\(onym)"))
	}

	@Test
	fun `parsing off keeps the raw name`() {
		ItemRow.parse = false
		assertEquals("They/(He and she) moved.", ItemRow.nameParser("They/(He and she) moved."))
	}

	@Test
	fun `a name with a bare slash but no group or escape stays as written`() {
		// no escaped slash and no closing parenthesis with a slash, so the parser never runs
		assertEquals("/I/You smile.", ItemRow.nameParser("/I/You smile."))
	}
}
