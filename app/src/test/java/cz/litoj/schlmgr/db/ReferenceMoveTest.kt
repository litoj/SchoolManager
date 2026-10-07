package cz.litoj.schlmgr.db

import androidx.room.Room
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
 *   container through the link alone;
 * - nothing is placed into itself or into its own content: that link would close a
 *   cycle the graph walks could not leave;
 * - an item CAN move into its own ancestor (a harmless reparent the old, inverted
 *   paste guard used to block).
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
		// a move would close a cycle too — the old link stays
		assertFalse(ItemRepository.move(outer.id, subject.id, inner.id))
		assertEquals(listOf("outer"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(listOf("inner"), ItemRepository.children(outer.id).map { it.name })
	}

	@Test
	fun `an item can move into its own ancestor`() {
		val subject = ItemRepository.createSubject("s", null)
		val outer = ItemRepository.createChapter(subject.id, "outer", null)
		val inner = ItemRepository.createChapter(outer.id, "inner", null)

		// the target is an ancestor of the moved item — a reparent, not a cycle
		assertTrue(ItemRepository.move(inner.id, outer.id, subject.id))
		assertEquals(listOf("outer", "inner"), ItemRepository.children(subject.id).map { it.name })
	}
}
