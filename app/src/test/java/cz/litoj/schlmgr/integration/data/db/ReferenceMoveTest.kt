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
 * The paste operations ([ItemRepository.reference], [ItemRepository.move]) on a real
 * in-memory database:
 * - any item can be referenced, a chapter included — its content shows in the new
 *   container through the link alone.
 * - nothing is placed into itself or into its own content — that link would close a
 *   cycle the graph walks could not leave.
 * - an item can still move into its own ancestor — a harmless reparent.
 * - a linked word's test counters follow it — the gaining chapter's stored counters
 *   refresh, and a move refreshes the losing one's too.
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

		// the chapter shows in the new container and its content comes with it
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

		// a reference would close a cycle — into itself and into its own child
		assertFalse(ItemRepository.reference(outer.id, outer.id))
		assertFalse(ItemRepository.reference(outer.id, inner.id))
		// a move would close a cycle too — the existing link stays
		assertEquals(ItemRepository.MoveResult.REFUSED_CYCLE, ItemRepository.move(outer.id, subject.id, inner.id))
		assertEquals(listOf("outer"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(listOf("inner"), ItemRepository.children(outer.id).map { it.name })
	}

	@Test
	fun `an item can move into its own ancestor`() {
		val subject = ItemRepository.createSubject("s", null)
		val outer = ItemRepository.createChapter(subject.id, "outer", null)
		val inner = ItemRepository.createChapter(outer.id, "inner", null)

		// the target is an ancestor of the moved item — a reparent, not a cycle
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

		// no crash on the duplicate link — the row is simply already there
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

		// idempotent — the second reference reports placed and adds no link
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
		// the word's parents — c1 plain, c2 reference, nothing deeper
		// than the direct children leaks in
		assertEquals(setOf(c1.id, c2.id), links.filter { it.childId == word.id }.map { it.parentId }.toSet())
		assertTrue(links.all { it.childId == word.id })
	}

	@Test
	fun `referencing a word into a chapter refreshes the chapter's stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		// the answer bumps the word and its chapter path together
		ItemRepository.bumpCounters(listOf(word.id, c1.id), false)

		assertTrue(ItemRepository.reference(word.id, c2.id))

		// the referenced word's tests joined the new parent's derivation
		assertEquals(1, ItemRepository.get(c2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(c2.id)!!.passedTests)
	}

	@Test
	fun `a shared container's every parent refreshes, the root never does`() {
		val subject = ItemRepository.createSubject("s", null)
		val c = ItemRepository.createChapter(subject.id, "c", null)
		// d references c — c's sums flow into d as well as the subject
		val d = ItemRepository.createChapter(subject.id, "d", null)
		assertTrue(ItemRepository.reference(c.id, d.id))
		val w1 = ItemRepository.createWord(c.id, "w1", null, listOf("t" to (null as String?)))
		val w2 = ItemRepository.createWord(c.id, "w2", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(w1.id, c.id, d.id), false)
		assertEquals(1, ItemRepository.get(d.id)!!.failedTests)

		// a new word joins c through a reference — every parent above c refreshes
		// (the subject and the referencing d)
		val outside = ItemRepository.createChapter(subject.id, "outside", null)
		val w3 = ItemRepository.createWord(outside.id, "w3", null, listOf("t" to (null as String?)))
		ItemRepository.bumpCounters(listOf(w3.id), true)
		assertTrue(ItemRepository.reference(w3.id, c.id))

		// d's subgraph holds w1 (one fail) and w3 (one pass)
		assertEquals(1, ItemRepository.get(d.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(d.id)!!.passedTests)
		// the invisible root row keeps its counters — the whole-database
		// sum writes nowhere
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

		// the word's tests left the source parent's derivation and joined the
		// target's
		assertEquals(0, ItemRepository.get(c1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(c2.id)!!.failedTests)
	}
}
