package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.testing.AnswerComparison
import cz.litoj.schlmgr.testing.AnswerMark.CORRECT
import cz.litoj.schlmgr.testing.AnswerMark.EXTRA
import cz.litoj.schlmgr.testing.AnswerMark.MISSING
import cz.litoj.schlmgr.testing.AnswerPart
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerComparisonTest {

	private fun comparison(answer: String, target: String) = AnswerComparison.compare(answer, target)

	@Test
	fun `an exact answer is one correct part`() {
		assertEquals(
			AnswerComparison("house", 0, listOf(AnswerPart("house", CORRECT))),
			comparison("house", "house"),
		)
	}

	@Test
	fun `an omission inserts the missing target letter`() {
		assertEquals(
			listOf(AnswerPart("h", CORRECT), AnswerPart("o", MISSING), AnswerPart("use", CORRECT)),
			comparison("huse", "house").parts,
		)
	}

	@Test
	fun `an extra answer letter is marked extra`() {
		assertEquals(
			listOf(AnswerPart("ho", CORRECT), AnswerPart("u", EXTRA), AnswerPart("use", CORRECT)),
			comparison("houuse", "house").parts,
		)
	}

	@Test
	fun `a substitution shows the extra letter before the missing one`() {
		val result = comparison("hause", "house")

		assertEquals(1, result.distance)
		assertEquals(
			listOf(
				AnswerPart("h", CORRECT),
				AnswerPart("a", EXTRA),
				AnswerPart("o", MISSING),
				AnswerPart("use", CORRECT),
			),
			result.parts,
		)
	}

	@Test
	fun `an empty answer marks the whole target missing`() {
		assertEquals(
			AnswerComparison("house", 5, listOf(AnswerPart("house", MISSING))),
			comparison("", "house"),
		)
	}

	@Test
	fun `the nearest variant supplies the comparison target`() {
		val result = AnswerComparison.closest("renen", listOf("laufen", "rennen"))

		assertEquals("rennen", result.target)
		assertEquals(1, result.distance)
		assertEquals(listOf(AnswerPart("re", CORRECT), AnswerPart("n", MISSING),
			AnswerPart("nen", CORRECT)), result.parts)
	}

	@Test
	fun `an equal distance keeps the first target`() {
		assertEquals("cut", AnswerComparison.closest("cot", listOf("cut", "cat")).target)
	}

	@Test
	fun `no target marks the whole answer extra`() {
		assertEquals(
			AnswerComparison("", 3, listOf(AnswerPart("abc", EXTRA))),
			AnswerComparison.closest("abc", emptyList()),
		)
	}

	@Test
	fun `adjacent letters with the same mark share one part`() {
		assertEquals(listOf(AnswerPart("ab", EXTRA)), comparison("ab", "").parts)
	}

	@Test
	fun `an empty answer and target have no parts`() {
		assertEquals(AnswerComparison("", 0, emptyList()), comparison("", ""))
	}
}
