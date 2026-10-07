package cz.litoj.schlmgr.db

import org.junit.Assert.assertEquals
import org.junit.Test
import cz.litoj.schlmgr.db.dao.WordDao

/**
 * Unit tests of the word-string variant parsing on [WordDao] — the port of the
 * former engine `NameReader`/`SimpleReader` codecs. The examples come from the
 * original documentation, so a mismatch means the port changed behaviour.
 */
class WordNameParseTest {

	/**
	 * Expected outputs verified against the original engine parser (its javadoc
	 * examples were slightly inaccurate — these are the actual results).
	 */
	@Test
	fun documentedVariantExamples() {
		assertEquals(
			listOf("They moved.", "He and she moved."),
			WordDao.parseVariants("They/(He and she) moved.")
		)
		assertEquals(
			listOf("smile.", "I smile.", "You smile."),
			WordDao.parseVariants("/I/You smile.")
		)
		assertEquals(
			listOf("I smile.", "You smile.", "Smile!"),
			WordDao.parseVariants("(I/You smile.)/(Smile!)")
		)
		assertEquals(
			listOf("I 'm here.", "I 'm there.", "I am here.", "I am there."),
			WordDao.parseVariants("I 'm/am here/there.")
		)
		assertEquals(
			listOf("We 're ", "We 're out.", "May you 're ", "May you 're out.", "He's ", "He's out."),
			WordDao.parseVariants("(We/(May you) 're)/He's /out.")
		)
	}

	@Test
	fun noSlashMeansSingleName() {
		assertEquals(listOf("plain name"), WordDao.parseVariants("plain name"))
	}

	@Test
	fun escapedSlashIsNotAVariant() {
		assertEquals(listOf("a/b c"), WordDao.parseVariants("a\\/b c"))
	}

	@Test
	fun trailingBackslashIsDroppedNotCrashedOn() {
		// the parser only runs for names with a `/`; a trailing `\` escapes
		// nothing at the end of the source
		assertEquals(WordDao.parseVariants("a/b"), WordDao.parseVariants("a/b\\"))
	}

	@Test
	fun splitEscapedDividesNames() {
		assertEquals(
			listOf("word one", "word two"),
			WordDao.splitEscaped("word one\\word two")
		)
	}

	@Test
	fun splitEscapedKeepsVariantSyntax() {
		// `\\` and the `/` stay part of one name — no split, input unchanged
		assertEquals(
			listOf("he\\\\/she"),
			WordDao.splitEscaped("he\\\\/she")
		)
	}

	@Test
	fun splitEscapedKeepsSingleName() {
		assertEquals(listOf("only"), WordDao.splitEscaped("only"))
	}
}
