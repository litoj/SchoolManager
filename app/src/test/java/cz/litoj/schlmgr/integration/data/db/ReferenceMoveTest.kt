package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ROOT_ID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - any item can be referenced, a chapter included.
 * - a referenced chapter shows its content through the link alone.
 * - nothing is placed into itself or into its own content.
 * - such a link would close a cycle that the graph walks could not leave.
 * - an item can still move into its own ancestor, a harmless reparent.
 * - the test counters of a linked word follow it through a move.
 * - a move refreshes both the losing and the gaining chain.
 * - a reference never counts, so its tests never enter its stored rate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReferenceMoveTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun `a chapter can be referenced into another chapter`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		ItemRepository.createWord(c1.id, "dog", null, listOf("pes" to (null as String?)))

		assertTrue(ItemRepository.reference(c1.id, c2.id))

		assertTrue(ItemRepository.linkIsReference(c2.id, c1.id))
		assertEquals(listOf("c1"), ItemRepository.children(c2.id).map { it.name })
		assertEquals(listOf("dog"),
			ItemRepository.children(ItemRepository.children(c2.id).single().id).map { it.name })
	}

	@Test
	fun `nothing is placed into itself or into its own content`() {
		val subject = ItemRepository.createSubject("s", null)
		val outer = ItemRepository.createChapter(subject.id, "outer", null)
		val inner = ItemRepository.createChapter(outer.id, "inner", null)

		// a reference would close a cycle, into itself and into its own child
		assertFalse(ItemRepository.reference(outer.id, outer.id))
		assertFalse(ItemRepository.reference(outer.id, inner.id))
		// a move would close a cycle too, so the existing link stays
		assertEquals(ItemRepository.MoveResult.REFUSED_CYCLE, ItemRepository.move(outer.id, subject.id, inner.id))
		assertEquals(listOf("outer"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(listOf("inner"), ItemRepository.children(outer.id).map { it.name })
	}

	@Test
	fun `an item can move into its own ancestor`() {
		val subject = ItemRepository.createSubject("s", null)
		val outer = ItemRepository.createChapter(subject.id, "outer", null)
		val inner = ItemRepository.createChapter(outer.id, "inner", null)

		// the target is an ancestor of the moved item, a reparent, not a cycle
		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(inner.id, outer.id, subject.id))
		assertEquals(listOf("outer", "inner"), ItemRepository.children(subject.id).map { it.name })
	}

	@Test
	fun `a move onto an already-linked container reports already-present`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(word.id, c2.id))

		// no crash on the duplicate link, because the row is already there
		assertEquals(ItemRepository.MoveResult.ALREADY_PRESENT, ItemRepository.move(word.id, c1.id, c2.id))
		assertEquals(listOf(word.id), ItemRepository.children(c1.id).map { it.id })
		assertEquals(listOf(word.id), ItemRepository.children(c2.id).map { it.id })
	}

	@Test
	fun `referencing an already-referenced row changes nothing`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(word.id, c2.id))

		// it is idempotent, so the second reference reports placed and adds no link
		assertTrue(ItemRepository.reference(word.id, c2.id))
		assertEquals(2, ItemRepository.parentIds(word.id).size)
	}

	@Test
	fun `childLinksOf returns the direct children's whole parent sets`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(word.id, c2.id))

		val links = ItemRepository.childLinksOf(c1.id)
		// the parents of the word: c1 plain and c2 a reference, with nothing
		// deeper than the direct children
		assertEquals(setOf(c1.id, c2.id), links.filter { it.childId == word.id }.map { it.parentId }.toSet())
		assertTrue(links.all { it.childId == word.id })
	}

	@Test
	fun `referencing a word into a chapter changes no stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and its chapter path together
		ItemRepository.bumpCounters(listOf(word.id, c1.id), false)

		assertTrue(ItemRepository.reference(word.id, c2.id))

		// the reference shows content and never tests, so c2 keeps its own sums
		assertEquals(0, ItemRepository.get(c2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(c2.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(c1.id)!!.failedTests)
	}

	@Test
	fun `a referencing parent never gains the referenced tests, the root never does`() {
		val subject = ItemRepository.createSubject("s", null)
		val c = ItemRepository.createChapter(subject.id, "c", null)
		// d references c, so the content of c shows in d and its tests never count there
		val d = ItemRepository.createChapter(subject.id, "d", null)
		assertTrue(ItemRepository.reference(c.id, d.id))
		val w1 = ItemRepository.createWord(c.id, "w1", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(subject.id, c.id, w1.id), false)

		// d shows w1 through the link but keeps its own empty sums
		assertEquals(0, ItemRepository.get(d.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.failedTests)

		// a word joins c through a reference, so no stored sum changes above
		val outside = ItemRepository.createChapter(subject.id, "outside", null)
		val w3 = ItemRepository.createWord(outside.id, "w3", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(subject.id, outside.id, w3.id), true)
		assertTrue(ItemRepository.reference(w3.id, c.id))
		assertEquals(0, ItemRepository.get(c.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.passedTests)
		// the invisible root row keeps its counters, so the whole-database sum
		// writes nowhere
		assertEquals(0, ItemRepository.get(ROOT_ID)!!.failedTests)
		assertEquals(0, ItemRepository.get(ROOT_ID)!!.passedTests)
	}

	@Test
	fun `moving a word between chapters refreshes both chapters' stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(word.id, c1.id), false)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(word.id, c1.id, c2.id))

		// the tests of the word left the derivation of the source parent and joined
		// the one of the target
		assertEquals(0, ItemRepository.get(c1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(c2.id)!!.failedTests)
	}

	@Test
	fun `a move refreshes every ancestor above both parents`() {
		val subject = ItemRepository.createSubject("s", null)
		val a1 = ItemRepository.createChapter(subject.id, "a1", null)
		val a2 = ItemRepository.createChapter(a1.id, "a2", null)
		val b1 = ItemRepository.createChapter(subject.id, "b1", null)
		val b2 = ItemRepository.createChapter(b1.id, "b2", null)
		val word = ItemRepository.createWord(a2.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(word.id, subject.id, a1.id, a2.id), false)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(word.id, a2.id, b2.id))

		// the losing chain drops the word, the gaining one takes it, and the shared
		// subject meets both deltas, so its sum stays
		assertEquals(0, ItemRepository.get(a2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(a1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(b2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(b1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.failedTests)
	}

	@Test
	fun `moving a chapter moves its words' counters with it`() {
		val subject = ItemRepository.createSubject("s", null)
		val a1 = ItemRepository.createChapter(subject.id, "a1", null)
		val b1 = ItemRepository.createChapter(subject.id, "b1", null)
		val inner = ItemRepository.createChapter(a1.id, "inner", null)
		val word = ItemRepository.createWord(inner.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and every container holding it
		ItemRepository.bumpWordCounters(word.id, true)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(inner.id, a1.id, b1.id))

		assertEquals(0, ItemRepository.get(a1.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(b1.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.passedTests)
	}

	@Test
	fun `a shared word's move keeps the surviving holder's sums`() {
		val subject = ItemRepository.createSubject("s", null)
		val mid = ItemRepository.createChapter(subject.id, "mid", null)
		val a1 = ItemRepository.createChapter(mid.id, "a1", null)
		val a2 = ItemRepository.createChapter(mid.id, "a2", null)
		val b1 = ItemRepository.createChapter(subject.id, "b1", null)
		val word = ItemRepository.createWord(a1.id, "w", null, listOf("t" to (null as String?)))
		// a second plain holder, so the word stays reachable through it
		ItemRepository.legacyLinkAtEnd(a2.id, word.id)
		ItemRepository.recomputeCounters(a2.id)
		ItemRepository.bumpCounters(listOf(word.id, subject.id, mid.id, a1.id, a2.id), false)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(word.id, a1.id, b1.id))

		// the word left a1 and joined b1 but never left a2, so a plain delta would
		// have deducted it from the sums of mid and the subject
		assertEquals(0, ItemRepository.get(a1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(a2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(mid.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(b1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.failedTests)
	}

	@Test
	fun `removing a word deducts its counters from the whole chain`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(c1.id, "c2", null)
		val word = ItemRepository.createWord(c2.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(word.id, subject.id, c1.id, c2.id), false)

		ItemRepository.remove(word.id, c2.id)

		assertEquals(0, ItemRepository.get(c2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(c1.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(subject.id)!!.failedTests)
	}

	@Test
	fun `removing a shared word keeps the surviving holder's sums`() {
		val subject = ItemRepository.createSubject("s", null)
		val a1 = ItemRepository.createChapter(subject.id, "a1", null)
		val a2 = ItemRepository.createChapter(subject.id, "a2", null)
		val word = ItemRepository.createWord(a1.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.legacyLinkAtEnd(a2.id, word.id)
		ItemRepository.recomputeCounters(a2.id)
		ItemRepository.bumpCounters(listOf(word.id, subject.id, a1.id, a2.id), false)

		ItemRepository.remove(word.id, a1.id)

		// the word left a1 only, because it stays in a2, so no sum above drops it
		assertEquals(0, ItemRepository.get(a1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(a2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.failedTests)
	}

	@Test
	fun `removing a reference changes no stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		val own = ItemRepository.createWord(c2.id, "own", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(word.id, c2.id))
		// both chapters carry the tests of their own plain words
		ItemRepository.bumpCounters(listOf(word.id, subject.id, c1.id), false)
		ItemRepository.bumpCounters(listOf(own.id, subject.id, c2.id), false)

		ItemRepository.remove(word.id, c2.id)

		// the severed reference never counted, so no stored sum moves
		assertEquals(1, ItemRepository.get(c1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(c2.id)!!.failedTests)
		assertEquals(2, ItemRepository.get(subject.id)!!.failedTests)
	}
}
