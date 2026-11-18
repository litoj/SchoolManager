package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the facts of one batch and one target, the button reads them and no rule of its own.
 * - a folder counts as its own content, a reference never leaves its set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PasteFactsTest {

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
	fun `the paste facts name every refusal`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null, 0)
		val c2 = ItemRepository.createChapter(a.id, "c2", null, 1)
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// the one row of c1 that already sits in the target too
		val shared = ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		ItemRepository.reference(shared.id, c2.id)
		val foreign = ItemRepository.createSubject("C", null)
		val stranger = ItemRepository.createChapter(foreign.id, "c9", null, 0)
		val linked = ItemRepository.children(c2.id).single()

		val rows = listOf(c1.id, c2.id, stranger.id, linked.id)
		val facts = ItemRepository.pasteFacts(rows, c2.id)

		assertFalse("a sibling folder reaches nothing", facts[0].inside || facts[0].present)
		assertTrue("a folder counts as its own content", facts[1].inside)
		assertTrue("the foreign row belongs to another set", facts[2].otherSet)
		assertTrue("the linked word already sits in the folder", facts[3].present)
		assertFalse("the rows of one set stay in it", facts[0].otherSet || facts[1].otherSet)
	}
}
