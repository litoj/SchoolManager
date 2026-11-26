package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the source expansion visits each item once.
 * - a word linked under sibling chapters is one test entry.
 * - that holds under the reference model, not once per container.
 * - one answer thus bumps its counters once.
 */
class TestEngineConvertAllTest {

	private fun item(id: Int, name: String, type: ItemKind) =
		DbItem(id = id, name = name, description = "", type = type, passedTests = 0, failedTests = 0)

	/** The fake tree: what the engine asks of the repository while expanding. */
	private val children = HashMap<Int, List<DbItem>>()

	private fun link(parent: DbItem, vararg items: DbItem) {
		children[parent.id] = items.toList()
	}

	private fun convertAll(vararg sources: List<DbItem>) =
		TestEngine.convertAll(sources.toList()) { id -> children[id] ?: emptyList() }

	@Test
	fun `a word referenced in sibling chapters is one entry`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		val a = item(2, "a", ItemKind.CHAPTER)
		val b = item(3, "b", ItemKind.CHAPTER)
		val w = item(4, "w", ItemKind.WORD)
		link(s, a, b)
		link(a, w)
		link(b, w)
		val entries = convertAll(listOf(s))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}

	@Test
	fun `a chapter reached through two paths expands once`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		val a = item(2, "a", ItemKind.CHAPTER)
		val b = item(3, "b", ItemKind.CHAPTER)
		val w = item(4, "w", ItemKind.WORD)
		// b sits under the subject and under a, so the word is reachable directly
		// through a and again through a→b
		link(s, a, b)
		link(a, b, w)
		val entries = convertAll(listOf(s))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}

	@Test
	fun `a selected word is a direct entry`() {
		val w = item(4, "w", ItemKind.WORD)
		val entries = convertAll(listOf(w))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}

	/** A word with [passed] of [tests] answers right, no answers meaning untested. */
	private fun rated(id: Int, passed: Int, tests: Int) =
		DbItem(id = id, name = "w$id", description = "", type = ItemKind.WORD,
			passedTests = passed, failedTests = tests - passed)

	/** Converts under [maxRate], then restores the open limit for the other cases. */
	private fun convertUnder(maxRate: Int, vararg sources: List<DbItem>) = try {
		TestEngine.applyMaxRate(maxRate)
		convertAll(*sources).map { it.word.id }
	} finally {
		TestEngine.applyMaxRate(100)
	}

	@Test
	fun `a rate limit lets in only the words at or below it`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		link(s, rated(2, passed = 0, tests = 2), rated(3, passed = 1, tests = 2), rated(4, passed = 2, tests = 2))

		assertEquals(listOf(2, 3), convertUnder(50, listOf(s)))
	}

	@Test
	fun `an untested word counts as 0 percent under the limit`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		link(s, rated(2, passed = 0, tests = 0), rated(3, passed = 0, tests = 3), rated(4, passed = 1, tests = 3))

		assertEquals(listOf(2, 3), convertUnder(0, listOf(s)))
	}

	@Test
	fun `the full limit keeps every word`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		link(s, rated(2, passed = 0, tests = 0), rated(3, passed = 3, tests = 3))

		assertEquals(listOf(2, 3), convertUnder(100, listOf(s)))
	}

	@Test
	fun `overlapping sources expand each word once`() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		val a = item(2, "a", ItemKind.CHAPTER)
		val w = item(4, "w", ItemKind.WORD)
		link(s, a)
		link(a, w)
		val entries = convertAll(listOf(s), listOf(a))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}
}
