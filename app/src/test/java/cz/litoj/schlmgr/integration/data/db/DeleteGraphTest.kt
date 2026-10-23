package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
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
 * - a subject wider than the 999 parameter cap of SQLite deletes fine.
 * - the walk handles one row at a time.
 * - an item shared by several parents survives the deletion of all but one.
 * - the tests of a removed word leave the stored counters of its chapter.
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
		ItemRepository.legacyLinkAtEnd(ROOT_ID, subject)
		val chapter = ItemRepository.legacyInsert("c", null, ItemKind.CHAPTER, 0, 0)
		ItemRepository.legacyLinkAtEnd(subject, chapter)
		// one breadth of 1001 children, past the 999 sqlite parameter cap
		repeat(1001) { i ->
			val word = ItemRepository.legacyInsert("w$i", null, ItemKind.WORD, 0, 0)
			ItemRepository.legacyLinkAtEnd(chapter, word)
		}

		ItemRepository.remove(subject, ROOT_ID)

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

		assertEquals(listOf("dog"), ItemRepository.children(c2.id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(word.id).map { it.name })
	}

	@Test
	fun `the deletion reports every removed row`() {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		ItemRepository.createWord(chapter.id, "w", null, listOf("t" to null))

		var removed = 0
		ItemRepository.remove(subject.id, ROOT_ID) { removed++ }

		// the subject, the chapter, the word and its translation, the count a
		// deletion bar shows
		assertEquals(4, removed)
	}

	@Test
	fun `removing a word drops its chapter's stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to null))
		// the answer bumps the word and its chapter path together
		ItemRepository.bumpCounters(listOf(word.id, c1.id), false)
		assertEquals(1, ItemRepository.get(c1.id)!!.failedTests)

		ItemRepository.remove(word.id, c1.id)

		val stored = ItemRepository.get(c1.id)!!
		assertEquals(0, stored.failedTests)
		assertEquals(0, stored.passedTests)
	}

	@Test
	fun `removing a word's last translation takes the word with it`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t1" to null, "t2" to null))
		val t1 = ItemRepository.translations(word.id).first { it.name == "t1" }
		val t2 = ItemRepository.translations(word.id).first { it.name == "t2" }
		// a second word shares t2, so the row keeps serving it
		val word2 = ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))

		// a non-last translation removes alone, the word stays
		ItemRepository.remove(t1.id, word.id)
		assertEquals(listOf("t2"), ItemRepository.translations(word.id).map { it.name })

		// the last one goes, and the word with it, while the shared row survives
		ItemRepository.remove(t2.id, word.id)
		assertEquals(null, ItemRepository.get(word.id))
		assertEquals(listOf("t2"), ItemRepository.translations(word2.id).map { it.name })

		// the last translation of word2 cascades it away too, with the derivation
		// of the chapter following the dying words down
		ItemRepository.bumpCounters(listOf(word2.id, c1.id), true)
		ItemRepository.remove(t2.id, word2.id)
		assertEquals(null, ItemRepository.get(word2.id))
		// the last parent of t2 died with word2, so the unshared row goes too,
		// not orphaned
		assertEquals(null, ItemRepository.get(t2.id))
		assertEquals(0, ItemRepository.get(c1.id)!!.passedTests)
	}
}
