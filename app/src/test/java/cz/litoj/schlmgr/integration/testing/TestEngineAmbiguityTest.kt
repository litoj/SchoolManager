package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import kotlin.math.abs
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
 * Ambiguous tests: shared prompts group, repeated answers do not pass twice.
 * - the setup reads a real in-memory database, since the prompt links decide the match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestEngineAmbiguityTest {

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
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	private fun entry(word: DbItem) = TestEntry(word, listOf(word))

	private fun subjectWith(name: String, words: List<Pair<String, List<String>>>): Pair<DbItem, DbItem> {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, name, null)
		words.forEach { (word, translations) ->
			ItemRepository.createWord(chapter.id, word, null, translations.map { it to null })
		}
		return subject to chapter
	}

	private fun entries(chapter: DbItem): List<TestEntry> =
		ItemRepository.children(chapter.id).filter { it.type == cz.litoj.schlmgr.data.db.ItemKind.WORD }
			.map(::entry)

	private fun answerFirstAndRepeat(entries: List<TestEntry>): Pair<Boolean, Boolean> {
		val engine = TestEngine()
		engine.setTested(-1, null, 1, entries)
		val picked = engine.getTestSrc()
		val name = picked[0].word.name
		val first = engine.isAnswer(0, name, picked[0].translations())
		val repeat = engine.isAnswer(1, name, picked[1].translations())
		return first to repeat
	}

	@Test
	fun `a padded answer matches like its bare form`() {
		val (_, chapter) = subjectWith("c", listOf("first" to listOf("same")))
		val entries = entries(chapter)
		val engine = TestEngine()
		engine.setTested(1, null, 1, entries)

		assertTrue("spaces are not part of the answer",
			engine.isAnswer(0, "  first  ", entries[0].translations()))
	}

	@Test
	fun `shared translation entries are adjacent`() {
		val (_, chapter) = subjectWith("c", listOf(
			"first" to listOf("same"),
			"other" to listOf("other"),
			"second" to listOf("same"),
			"last" to listOf("last"),
		))
		val entries = entries(chapter)
		val engine = TestEngine()
		engine.setTested(-1, null, 1, entries)
		val names = engine.getTestSrc().map { it.word.name }

		assertTrue("the shared pair lands next to each other: $names",
			abs(names.indexOf("first") - names.indexOf("second")) == 1)
	}

	@Test
	fun `a second correct answer to the same prompt fails`() {
		val (_, chapter) = subjectWith("c", listOf(
			"first" to listOf("same"), "second" to listOf("same"),
		))
		val (first, second) = answerFirstAndRepeat(entries(chapter))

		assertTrue("the first prompt answers once", first)
		assertFalse("a repeated correct answer cannot pass a second time", second)
	}

	@Test
	fun `identical names on unshared prompts still pass independently`() {
		val (_, c1) = subjectWith("c1", listOf("sameName" to listOf("one")))
		val (_, c2) = subjectWith("c2", listOf("sameName" to listOf("two")))
		val (first, second) = answerFirstAndRepeat(entries(c1) + entries(c2))

		assertTrue("an unrelated prompt answers by its own word", first)
		assertTrue("the same text can answer an unambiguous prompt too", second)
	}

	@Test
	fun `different correct answers to a shared prompt pass`() {
		val (_, chapter) = subjectWith("c", listOf(
			"first" to listOf("same"), "second" to listOf("same"),
		))
		val entries = entries(chapter)
		val engine = TestEngine()
		engine.setTested(-1, null, 1, entries)
		val picked = engine.getTestSrc()

		assertTrue("the first different answer passes",
			engine.isAnswer(0, picked[0].word.name, picked[0].translations()))
		assertTrue("the second different answer passes",
			engine.isAnswer(1, picked[1].word.name, picked[1].translations()))
	}

	@Test
	fun `a matching word for only one shown translation still fails`() {
		val (_, chapter) = subjectWith("c", listOf(
			"target" to listOf("same", "other"),
			"wrong" to listOf("same"),
		))
		val target = ItemRepository.children(chapter.id).single { it.name == "target"
			&& it.type == cz.litoj.schlmgr.data.db.ItemKind.WORD }
		val engine = TestEngine()
		engine.setTested(1, null, 1, listOf(entry(target)))

		assertFalse("matching only one shown translation cannot pass",
			engine.isAnswer(0, "wrong", entry(target).translations()))
	}
}
