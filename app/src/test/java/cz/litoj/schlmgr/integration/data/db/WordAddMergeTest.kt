package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
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
 * The interactive word-add merge ([ItemRepository.createWord]): a word whose
 * name exists in the subject scope merges into it instead of creating a second
 * row, and an added translation that exists merges its description. Pins the
 * merge rule the repository owns on every item add.
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
	}

	@Test
	fun `a spaced name is the same word - it trims before the merge`() {
		val subject = ItemRepository.createSubject("subject", null)
		val first = ItemRepository.createWord(
			subject.id, " word ", "first description", listOf(" one " to null))
		val second = ItemRepository.createWord(
			subject.id, "word", "second description", listOf("two" to null))

		// the trimmed names merge — one row, the stored name trimmed
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
			// the create refuses — a bare name is the note's shape
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
		// both descriptions and both translations joined the one row
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
	fun `a word without translations is refused`() {
		val subject = ItemRepository.createSubject("s", null)

		try {
			ItemRepository.createWord(subject.id, "lonely", null, emptyList())
			fail("a translation-less word must not be created — make a note instead")
		} catch (e: IllegalArgumentException) {
			// expected — every word carries at least one translation
		}
		assertTrue(ItemRepository.children(subject.id).isEmpty())
	}
}
