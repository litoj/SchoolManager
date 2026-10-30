package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - deletions leave gaps in the raw positions.
 * - the callers speak visual indexes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChildOrderTest {

	private lateinit var db: ItemDatabase
	private lateinit var subject: DbItem

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		subject = ItemRepository.createSubject("s", null)
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	private fun names(): List<String> = ItemRepository.children(subject.id).map { it.name }

	private fun remove(name: String) {
		val row = ItemRepository.children(subject.id).single { it.name == name }
		ItemRepository.remove(row.id, subject.id)
	}

	@Test
	fun `a create at the end lands after a gap`() {
		ItemRepository.createChapter(subject.id, "c1", null, 0)
		ItemRepository.createChapter(subject.id, "c2", null, 1)
		ItemRepository.createChapter(subject.id, "c3", null, 2)
		// the middle deletion leaves the raw positions gapped
		remove("c2")

		ItemRepository.createChapter(subject.id, "c4", null, 2)

		// the visual end index is 2, so the row lands after everything
		assertEquals(listOf("c1", "c3", "c4"), names())
	}

	@Test
	fun `a mid-list create pushes the occupant`() {
		ItemRepository.createChapter(subject.id, "c1", null, 0)
		ItemRepository.createChapter(subject.id, "c3", null, 1)

		ItemRepository.createChapter(subject.id, "c2", null, 1)

		assertEquals(listOf("c1", "c2", "c3"), names())
	}

	@Test
	fun `a mid-list create over a gap pushes the occupant`() {
		ItemRepository.createChapter(subject.id, "c1", null, 0)
		ItemRepository.createChapter(subject.id, "c2", null, 1)
		ItemRepository.createChapter(subject.id, "c3", null, 2)
		remove("c2")

		ItemRepository.createChapter(subject.id, "c4", null, 1)

		// the raw positions are gapped, so the visual slot 1 belongs to c3
		assertEquals(listOf("c1", "c4", "c3"), names())
	}

	@Test
	fun `a blank name refuses the chapter create`() {
		try {
			ItemRepository.createChapter(subject.id, "   ", null)
			fail("a chapter with no name cannot be stored, since no export format round-trips it")
		} catch (e: IllegalArgumentException) {
			// expected, because a blank name is not a chapter
		}
		assertTrue(ItemRepository.children(subject.id).isEmpty())
	}

	@Test
	fun `an order list short of the live children appends the unseen rows`() {
		for (name in listOf("c1", "c2", "c3", "c4", "c5"))
			ItemRepository.createChapter(subject.id, name, null)
		val c3 = ItemRepository.children(subject.id).first { it.name == "c3" }
		val c5 = ItemRepository.children(subject.id).first { it.name == "c5" }

		// the drag list holds c3 twice, a dead id and c5
		ItemRepository.setOrder(subject.id, listOf(c3.id, c3.id, 9_999, c5.id))

		// the drag order stands, the repeated id keeps one slot.
		// the dead id drops out, the unseen rows take the end in their live order
		assertEquals(listOf("c3", "c5", "c1", "c2", "c4"), names())
	}

	@Test
	fun `a reorder over a gap keeps the visual order`() {
		ItemRepository.createChapter(subject.id, "c1", null, 0)
		ItemRepository.createChapter(subject.id, "c2", null, 1)
		ItemRepository.createChapter(subject.id, "c3", null, 2)
		remove("c2")
		val c4 = ItemRepository.createChapter(subject.id, "c4", null, 2)

		// moving the end row onto its own visual slot is a no-op, because the
		// gapped raw position behind it must not shift anything
		ItemRepository.reorder(subject.id, c4.id, 2)
		assertEquals(listOf("c1", "c3", "c4"), names())

		ItemRepository.reorder(subject.id, c4.id, 0)
		assertEquals(listOf("c4", "c1", "c3"), names())
	}
}
