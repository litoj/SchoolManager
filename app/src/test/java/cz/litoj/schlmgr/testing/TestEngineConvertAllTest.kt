package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TestEngine.convertAll]: the source expansion visits each
 * item once — a word linked under sibling chapters (the reference model)
 * is one test entry, not one per container that reaches it, so one answer
 * bumps its counters once.
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
	fun wordReferencedInSiblingChaptersIsOneEntry() {
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
	fun chapterReachedThroughTwoPathsIsExpandedOnce() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		val a = item(2, "a", ItemKind.CHAPTER)
		val b = item(3, "b", ItemKind.CHAPTER)
		val w = item(4, "w", ItemKind.WORD)
		// b sits under the subject and under a — the word is reachable
		// directly through a and again through a→b
		link(s, a, b)
		link(a, b, w)
		val entries = convertAll(listOf(s))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}

	@Test
	fun selectedWordIsADirectEntry() {
		val w = item(4, "w", ItemKind.WORD)
		val entries = convertAll(listOf(w))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}

	@Test
	fun overlappingSourcesExpandEachWordOnce() {
		val s = item(1, "subject", ItemKind.CHAPTER)
		val a = item(2, "a", ItemKind.CHAPTER)
		val w = item(4, "w", ItemKind.WORD)
		link(s, a)
		link(a, w)
		// the subject and its own chapter both picked as sources
		val entries = convertAll(listOf(s), listOf(a))
		assertEquals(1, entries.size)
		assertEquals(w.id, entries[0].word.id)
	}
}
