package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DaoItemStore
import cz.litoj.schlmgr.data.db.ImportSession
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - a word whose name exists in the subject scope merges into that row.
 * - an added translation that exists merges its description.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordAddMergeTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
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
	fun `a spaced name is the same word - it trims before the merge`() {
		val subject = ItemRepository.createSubject("subject", null)
		val first = ItemRepository.createWord(
			subject.id, " word ", "first description", listOf(" one " to null))
		val second = ItemRepository.createWord(
			subject.id, "word", "second description", listOf("two" to null))

		// the trimmed names merge into one row, with the stored name trimmed
		assertEquals(first.id, second.id)
		val words = ItemRepository.children(subject.id)
		assertEquals(1, words.size)
		assertEquals("word", words.single().name)
		// the spaced translation trimmed and joined the word
		assertEquals(listOf("one", "two"), ItemRepository.translations(first.id).map { it.name })
	}

	@Test
	fun `a word whose every translation is blank is refused`() {
		val subject = ItemRepository.createSubject("subject", null)
		try {
			ItemRepository.createWord(subject.id, "word", null, listOf("   " to null))
			fail("A blank translation is no translation")
		} catch (e: IllegalArgumentException) {
			// the create refuses, because a bare name is the shape of a note
		}
	}

	@Test
	fun `adding a word whose name exists in the subject scope merges into it`() {
		val subject = ItemRepository.createSubject("subject", null)
		val first = ItemRepository.createWord(
			subject.id, "word", "first description", listOf("one" to null))
		val second = ItemRepository.createWord(
			subject.id, "word", "second description", listOf("two" to null))

		// the same row, not a second one
		assertEquals(first.id, second.id)
		val words = ItemRepository.children(subject.id)
		assertEquals(1, words.size)
		assertEquals("first description\nsecond description", words.single().description)
		assertEquals(listOf("one", "two"), ItemRepository.translations(first.id).map { it.name })
	}

	@Test
	fun `the same name under another subject does not merge`() {
		val one = ItemRepository.createSubject("one", null)
		val two = ItemRepository.createSubject("two", null)
		val w1 = ItemRepository.createWord(one.id, "word", null, listOf("trl" to null))
		val w2 = ItemRepository.createWord(two.id, "word", null, listOf("trl" to null))

		assertNotEquals(w1.id, w2.id)
		assertEquals(1, ItemRepository.children(one.id).size)
		assertEquals(1, ItemRepository.children(two.id).size)
	}

	@Test
	fun `an added translation that already exists merges its description`() {
		val subject = ItemRepository.createSubject("subject", null)
		val word = ItemRepository.createWord(subject.id, "word", null, listOf("trl" to "first"))
		ItemRepository.createWord(subject.id, "word", null, listOf("trl" to "second"))

		val translation = ItemRepository.translations(word.id).single()
		assertEquals("trl", translation.name)
		assertEquals("first\nsecond", translation.description)
	}

	@Test
	fun `a blank name refuses the word create`() {
		val subject = ItemRepository.createSubject("s", null)

		try {
			ItemRepository.createWord(subject.id, "   ", null, listOf("trl" to null))
			fail("a word with no name cannot be stored, since no export format round-trips it")
		} catch (e: IllegalArgumentException) {
			// expected, because a blank name is not a word
		}
		assertTrue(ItemRepository.children(subject.id).isEmpty())
	}

	@Test
	fun `a word without translations is refused`() {
		val subject = ItemRepository.createSubject("s", null)

		try {
			ItemRepository.createWord(subject.id, "lonely", null, emptyList())
			fail("a translation-less word must not be created, make a note instead")
		} catch (e: IllegalArgumentException) {
			// expected, because every word carries at least one translation
		}
		assertTrue(ItemRepository.children(subject.id).isEmpty())
	}

	@Test
	fun `a row without a plain home answers through its reference in both stores`() {
		val subjectA = ItemRepository.createSubject("english", null)
		val subjectB = ItemRepository.createSubject("deutsch", null)
		val holder = ItemRepository.createChapter(subjectB.id, "holder", null)
		val word = ItemRepository.createWord(subjectA.id, "dog", null, listOf("pes" to null))
		// the row survives on the reference alone after its plain home drops it
		ItemRepository.reference(word.id, holder.id)
		ItemRepository.remove(word.id, subjectA.id)

		// both stores share the merge scope, so each must find the row
		runBlocking {
			val dao = db.itemDao()
			val daoStore = DaoItemStore(dao)
			val bRoot = dao.subjectRootOf(holder.id, ROOT_ID)!!
			assertEquals(word.id, daoStore.findInSubject(bRoot, "dog", ItemKind.WORD)?.id)
			val session = ImportSession.load(dao, holder.id)
			assertEquals(word.id, session.findInSubject(bRoot, "dog", ItemKind.WORD)?.id)
		}
	}
}
