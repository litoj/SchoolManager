package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
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
 * [TestEngine.isAnswer] on a real in-memory database.
 * - the answer bump covers the word and every container whose sums hold it.
 * - a shared plain holder counts.
 * - a reference holder never counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestEngineAnswerTest {

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

	private fun answer(entry: TestEntry, text: String?): Boolean {
		val engine = TestEngine()
		engine.setTested(1, null, 1, listOf(entry))
		return engine.isAnswer(0, text, entry.translations())
	}

	@Test
	fun `a correct answer bumps the word and every plain holder`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		// the word is shared, so c2 holds it through a second plain link
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)

		assertTrue(answer(TestEntry(word, listOf(subject, c1, word)), "w"))

		// every container whose sums hold the word grew with it
		assertEquals(1, ItemRepository.get(word.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(c1.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(c2.id)!!.passedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.passedTests)
	}

	@Test
	fun `an answer never bumps a reference holder`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val ref = ItemRepository.createChapter(subject.id, "ref", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		assertTrue(ItemRepository.reference(word.id, ref.id))

		assertTrue(answer(TestEntry(word, listOf(subject, c1, word)), "w"))

		// the reference shows the word but never counts it
		assertEquals(0, ItemRepository.get(ref.id)!!.passedTests)
		assertEquals(0, ItemRepository.get(ref.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.passedTests)
	}

	@Test
	fun `a wrong answer fails the word, its holders and the shown translation`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(c1.id, "w", null, listOf("t" to (null as String?)))
		val entry = TestEntry(word, listOf(subject, c1, word))

		assertFalse(answer(entry, "nope"))

		assertEquals(1, ItemRepository.get(word.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(c1.id)!!.failedTests)
		assertEquals(1, ItemRepository.get(subject.id)!!.failedTests)
		val translation = ItemRepository.translations(word.id).single()
		assertEquals(1, translation.failedTests)
	}
}
