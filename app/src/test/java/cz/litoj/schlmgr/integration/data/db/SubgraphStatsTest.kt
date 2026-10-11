package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The import/export statistics ([ItemRepository.subgraphStats]): the distinct word
 * and chapter counts of the union of the given containers' subgraphs — a row under
 * several containers counts once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubgraphStatsTest {

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
	fun `counts each word and chapter of the union once`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		ItemRepository.createChapter(c1.id, "nested", null)
		ItemRepository.createWord(c1.id, "dog", null, listOf("pes" to (null as String?)))
		val shared = ItemRepository.createWord(c1.id, "cat", null, listOf("kočka" to (null as String?)))
		ItemRepository.reference(shared.id, c2.id)

		val (words, chapters) = ItemRepository.subgraphStats(listOf(c1.id, c2.id))

		assertEquals(2, words) // dog and cat — the shared cat once
		assertEquals(3, chapters) // c1, nested and c2 — the roots included
	}
}
