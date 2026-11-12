package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The seconds of a run: five per word plus the characters its answer writes.
 * - the tested side is the written one, so the direction decides whose length counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestEngineTimeTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
	}

	private fun entries(vararg words: Pair<String, List<String>>): List<TestEntry> {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		return words.map { (name, translations) ->
			val word = ItemRepository.createWord(chapter.id, name, null, translations.map { it to null })
			TestEntry(word, listOf(subject, chapter, word))
		}
	}

	@Test
	fun `the word name sets the seconds of the ordinary direction`() {
		val (short, long) = entries("cat" to listOf("kočka"), "responsibility" to listOf("povinnost"))

		assertEquals("five seconds plus three characters", 8, TestEngine.testTime(1f, listOf(short)))
		assertEquals(19, TestEngine.testTime(1f, listOf(long)))
		assertEquals("one run sums its words", 27, TestEngine.testTime(1f, listOf(short, long)))
	}

	@Test
	fun `the multiplier scales the written part only`() {
		val words = entries("cat" to listOf("kočka"), "responsibility" to listOf("povinnost"))

		assertEquals("the base stays, 6 and 28 characters cost double", 44, TestEngine.testTime(2f, words))
		assertEquals(95, TestEngine.testTime(5f, words))
	}

	@Test
	fun `the reversed direction counts the translation it writes`() {
		val word = entries("cat" to listOf("tail", "responsibility")).single()
		TestEngine.applyReversed(true)

		assertEquals("the longest answer sets the time", 19, TestEngine.testTime(1f, listOf(word)))
	}

	@Test
	fun `an added required variant costs one and a half times`() {
		val plain = entries("go" to listOf("jet")).single()
		val variant = entries("go/run" to listOf("jet")).single()

		assertEquals("the option is off, so one answer suffices", 11, TestEngine.testTime(1f, listOf(variant)))
		TestEngine.applyRequireAllVariants(true)

		assertEquals(7, TestEngine.testTime(1f, listOf(plain)))
		assertEquals("5 plus 6 characters over two fields", 17, TestEngine.testTime(1f, listOf(variant)))
	}
}
