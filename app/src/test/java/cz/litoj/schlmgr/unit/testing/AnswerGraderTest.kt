package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.AnswerGrader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderTest {

	private fun item(id: Int, name: String, kind: ItemKind) = DbItem(id = id, name = name, description = "", type = kind)

	@Test
	fun `synonyms come from the caller rather than persistence`() {
		val word = item(1, "target", ItemKind.WORD)
		val translation = item(2, "same", ItemKind.TRANSLATION)
		val parent = item(3, "synonym", ItemKind.WORD)
		val grader = AnswerGrader(reversed = false, parentsOf = { id ->
			if (id == translation.id) listOf(parent) else emptyList()
		})

		assertTrue(grader.matchesEntry(word, listOf(translation), "synonym"))
		assertFalse(grader.matchesEntry(word, listOf(translation), "unknown"))
	}

	@Test
	fun `reversed variants normalize once and credit only their matches`() {
		val word = item(1, "house", ItemKind.WORD)
		val first = item(2, "dům?", ItemKind.TRANSLATION)
		val second = item(3, "domov", ItemKind.TRANSLATION)
		val grader = AnswerGrader(reversed = true, ignoredMistakes = listOf("?"))

		assertTrue(grader.matchesAllVariants(word, listOf(first, second), listOf("dům", "domov")))
		assertEquals(listOf(first), grader.creditedTranslations(listOf(first, second), "dům"))
	}
}
