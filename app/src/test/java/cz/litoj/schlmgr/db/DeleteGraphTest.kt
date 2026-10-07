package cz.litoj.schlmgr.db

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The graph deletion behind [ItemRepository.remove] on a real in-memory database:
 * - a subject wider than SQLite's cap of 999 host parameters per statement must delete
 *   fine — the walk and the cleanup handle one row at a time, never a bulk `IN (...)`
 *   over a whole breadth;
 * - an item shared by several parents survives the deletion of all but one of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeleteGraphTest {

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
	fun `removing a subject wider than the sqlite parameter cap deletes everything`() {
		val subject = ItemRepository.legacyInsert("big", null, ItemKind.CHAPTER, 0, 0)
		ItemRepository.legacyLinkAtEnd(ItemRepository.ROOT_ID, subject)
		val chapter = ItemRepository.legacyInsert("c", null, ItemKind.CHAPTER, 0, 0)
		ItemRepository.legacyLinkAtEnd(subject, chapter)
		// one breadth of 1001 children: the former bulk query crossed the 999 cap here
		repeat(1001) { i ->
			val word = ItemRepository.legacyInsert("w$i", null, ItemKind.WORD, 0, 0)
			ItemRepository.legacyLinkAtEnd(chapter, word)
		}

		ItemRepository.remove(subject, ItemRepository.ROOT_ID)

		assertTrue(ItemRepository.subjects().isEmpty())
		assertTrue(ItemRepository.getByName("c", ItemKind.CHAPTER).isEmpty())
		assertTrue(ItemRepository.getByName("w1000", ItemKind.WORD).isEmpty())
	}

	@Test
	fun `a word shared by two chapters survives deleting one of them`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "dog", null, listOf("pes" to (null as String?)))
		ItemRepository.reference(word.id, c2.id)

		ItemRepository.remove(c1.id, subject.id)

		// the word and its translation live on through the other chapter
		assertEquals(listOf("dog"), ItemRepository.children(c2.id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(word.id).map { it.name })
	}
}
