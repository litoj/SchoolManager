package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestEngineIgnoredMistakesTest {

	@Before
	fun setUp() {
		TestEngine.applyReversed(false)
		TestEngine.applyIgnoredMistakes("")
	}

	@After
	fun tearDown() {
		TestEngine.applyReversed(false)
		TestEngine.applyIgnoredMistakes("")
	}

	private fun matchesWord(expected: String, answer: String): Boolean {
		val word = DbItem(name = expected, description = "", type = ItemKind.WORD)
		return TestEngine.matchesAnswer(word, emptyList(), answer)
	}

	@Test
	fun `configured text can be missing or extra on either answer side`() {
		TestEngine.applyIgnoredMistakes("。\n\n、\n \n...\n?\n")

		assertTrue(matchesWord("こんにちは。", "こんにちは"))
		assertTrue(matchesWord("こんにちは", " こんにちは...?"))
		assertTrue(matchesWord("a、b", "ab"))
		assertFalse(matchesWord("こんにちは。", "こんにちは!"))
	}

	@Test
	fun `a multi-character entry removes only the whole literal`() {
		TestEngine.applyIgnoredMistakes("...")

		assertTrue(matchesWord("done...", "done"))
		assertFalse(matchesWord("done.", "done"))
	}

	@Test
	fun `reversed matching and translation credit use the same comparison`() {
		TestEngine.applyIgnoredMistakes("?")
		TestEngine.applyReversed(true)
		val word = DbItem(name = "house", description = "", type = ItemKind.WORD)
		val translation = DbItem(name = "home?", description = "", type = ItemKind.TRANSLATION)
		val translations = listOf(translation)

		assertTrue(TestEngine.matchesAnswer(word, translations, "home"))
		assertEquals(translations, TestEngine.creditedTranslations(translations, "home"))
	}
}
