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
 * - a reference link changes no stored counter, added or severed.
 * - a move recomputes every chain it touches, a surviving holder keeps its sums.
 * - a removal deducts from its chain, a surviving holder keeps its sums.
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
		ItemRepository.resetForTest()
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

	/** A reference link only shows content, so no stored counter moves, added or severed. */
	@Test
	fun `a reference link changes no stored counters`() {
		val one = ItemRepository.createSubject("one", null)
		val home = ItemRepository.createChapter(one.id, "home", null)
		val shown = ItemRepository.createChapter(one.id, "shown", null)
		val word = ItemRepository.createWord(home.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and its chapter path together
		ItemRepository.bumpCounters(listOf(word.id, home.id), false)

		assertTrue(ItemRepository.reference(word.id, shown.id))

		// the reference shows content and never tests, so its row keeps its own sums
		assertEquals(0, ItemRepository.get(shown.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(shown.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(home.id)!!.failedTests)

		// a referencing chapter never gains the tests of the content it only shows
		val two = ItemRepository.createSubject("two", null)
		val content = ItemRepository.createChapter(two.id, "content", null)
		val parent = ItemRepository.createChapter(two.id, "parent", null)
		assertTrue(ItemRepository.reference(content.id, parent.id))
		val inner = ItemRepository.createWord(content.id, "w1", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(two.id, content.id, inner.id), false)
		assertEquals(0, ItemRepository.get(parent.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(two.id)!!.failedTests)

		// a word joining the shown chapter changes nothing above it either
		val outside = ItemRepository.createChapter(two.id, "outside", null)
		val outsideWord = ItemRepository.createWord(outside.id, "w3", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(two.id, outside.id, outsideWord.id), true)
		assertTrue(ItemRepository.reference(outsideWord.id, content.id))
		assertEquals(0, ItemRepository.get(content.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(two.id)!!.passedTests)

		// a severed reference never counted, so no stored sum moves
		val three = ItemRepository.createSubject("three", null)
		val otherHome = ItemRepository.createChapter(three.id, "home", null)
		val referrer = ItemRepository.createChapter(three.id, "referrer", null)
		val shared = ItemRepository.createWord(otherHome.id, "w", null, listOf("t" to (null as String?)))
		val own = ItemRepository.createWord(referrer.id, "own", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(shared.id, referrer.id))
		// both chapters carry the tests of their own plain words
		ItemRepository.bumpCounters(listOf(shared.id, three.id, otherHome.id), false)
		ItemRepository.bumpCounters(listOf(own.id, three.id, referrer.id), false)

		ItemRepository.remove(shared.id, referrer.id)

		assertEquals(1, ItemRepository.get(otherHome.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(referrer.id)!!.failedTests)
		assertEquals(2, ItemRepository.get(three.id)!!.failedTests)
		// the invisible root row keeps its counters, so the whole-database sum
		// writes nowhere
		assertEquals(0, ItemRepository.get(ROOT_ID)!!.failedTests)
		assertEquals(0, ItemRepository.get(ROOT_ID)!!.passedTests)
	}

	/** A move recomputes every container of the chains it touched. */
	@Test
	fun `a move refreshes every chain it touches`() {
		val one = ItemRepository.createSubject("one", null)
		val a1 = ItemRepository.createChapter(one.id, "a1", null)
		val a2 = ItemRepository.createChapter(a1.id, "a2", null)
		val b1 = ItemRepository.createChapter(one.id, "b1", null)
		val b2 = ItemRepository.createChapter(b1.id, "b2", null)
		val word = ItemRepository.createWord(a2.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(word.id, one.id, a1.id, a2.id), false)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(word.id, a2.id, b2.id))

		// the losing chain drops the word, the gaining one takes it, and the shared
		// subject meets both deltas, so its sum stays
		assertEquals(0, ItemRepository.get(a2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(a1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(b2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(b1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(one.id)!!.failedTests)

		// the word keeps a second plain holder, so only the chain it really left drops it
		val two = ItemRepository.createSubject("two", null)
		val mid = ItemRepository.createChapter(two.id, "mid", null)
		val from1 = ItemRepository.createChapter(mid.id, "a1", null)
		val from2 = ItemRepository.createChapter(mid.id, "a2", null)
		val to1 = ItemRepository.createChapter(two.id, "b1", null)
		val held = ItemRepository.createWord(from1.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.legacyLinkAtEnd(from2.id, held.id)
		ItemRepository.recomputeCounters(from2.id)
		ItemRepository.bumpCounters(listOf(held.id, two.id, mid.id, from1.id, from2.id), false)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(held.id, from1.id, to1.id))

		// the word left a1 and joined b1 but never left a2, so a plain delta would
		// have deducted it from the sums of mid and the subject
		assertEquals(0, ItemRepository.get(from1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(from2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(mid.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(to1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(two.id)!!.failedTests)

		// a moved chapter carries the words inside it, and their counters with them
		val three = ItemRepository.createSubject("three", null)
		val under = ItemRepository.createChapter(three.id, "a1", null)
		val onto = ItemRepository.createChapter(three.id, "b1", null)
		val inner = ItemRepository.createChapter(under.id, "inner", null)
		val inside = ItemRepository.createWord(inner.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and every container holding it
		ItemRepository.bumpWordCounters(inside.id, true)

		assertEquals(ItemRepository.MoveResult.MOVED, ItemRepository.move(inner.id, under.id, onto.id))

		assertEquals(0, ItemRepository.get(under.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(onto.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(three.id)!!.passedTests)
	}

	/** A removal deducts the gone row's counters from every plain chain above it. */
	@Test
	fun `removing a word deducts its counters from the whole chain`() {
		val one = ItemRepository.createSubject("one", null)
		val c1 = ItemRepository.createChapter(one.id, "c1", null)
		val c2 = ItemRepository.createChapter(c1.id, "c2", null)
		val word = ItemRepository.createWord(c2.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and its whole path together
		ItemRepository.bumpCounters(listOf(word.id, one.id, c1.id, c2.id), false)
		ItemRepository.bumpCounters(listOf(word.id, one.id, c1.id, c2.id), true)

		ItemRepository.remove(word.id, c2.id)

		for ((label, id) in listOf("chapter" to c2.id, "its parent" to c1.id, "subject" to one.id)) {
			assertEquals("$label lost the failed sum", 0, ItemRepository.get(id)!!.failedTests)
			assertEquals("$label lost the passed sum", 0, ItemRepository.get(id)!!.passedTests)
		}

		// the word stays in a2, so no sum above drops it
		val two = ItemRepository.createSubject("two", null)
		val a1 = ItemRepository.createChapter(two.id, "a1", null)
		val a2 = ItemRepository.createChapter(two.id, "a2", null)
		val held = ItemRepository.createWord(a1.id, "w", null, listOf("t" to (null as String?)))
		ItemRepository.legacyLinkAtEnd(a2.id, held.id)
		ItemRepository.recomputeCounters(a2.id)
		ItemRepository.bumpCounters(listOf(held.id, two.id, a1.id, a2.id), false)

		ItemRepository.remove(held.id, a1.id)

		assertEquals(0, ItemRepository.get(a1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(a2.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(two.id)!!.failedTests)
	}
}
