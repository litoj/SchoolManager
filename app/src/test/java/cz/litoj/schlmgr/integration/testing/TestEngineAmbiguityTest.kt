package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTaskModel
import kotlin.math.abs
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
		TestEngine.applyRequireAllVariants(false)
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

	/** The second word links the exact translation row of the first. */
	private fun sharedPair(firstName: String, secondName: String): List<TestEntry> {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val first = ItemRepository.createWord(chapter.id, firstName, null, listOf("what" to null))
		val translation = ItemRepository.translations(first.id).single()
		val secondId = ItemRepository.legacyInsert(
			secondName, "", cz.litoj.schlmgr.data.db.ItemKind.WORD, 0, 0)
		val second = ItemRepository.get(secondId)!!
		ItemRepository.legacyLinkAtEnd(chapter.id, second.id)
		ItemRepository.legacyLinkAtEnd(second.id, translation.id)
		return listOf(
			TestEntry(first, listOf(subject, chapter, first)),
			TestEntry(second, listOf(subject, chapter, second)),
		)
	}

	/** The same translation text, but each word holds its own row. */
	private fun unsharedPair(): List<TestEntry> {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val first = ItemRepository.createWord(chapter.id, "なに/なん", null, listOf("what" to null))
		val secondId = ItemRepository.legacyInsert(
			"なん", "", cz.litoj.schlmgr.data.db.ItemKind.WORD, 0, 0)
		val second = ItemRepository.get(secondId)!!
		val translationId = ItemRepository.legacyInsert(
			"what", "", cz.litoj.schlmgr.data.db.ItemKind.TRANSLATION, 0, 0)
		ItemRepository.legacyLinkAtEnd(chapter.id, second.id)
		ItemRepository.legacyLinkAtEnd(second.id, translationId)
		return listOf(
			TestEntry(first, listOf(subject, chapter, first)),
			TestEntry(second, listOf(subject, chapter, second)),
		)
	}

	/** Grades like the screen: assign each task's typed answers, then answer every member. */
	private fun grades(engine: TestEngine, raw: List<List<String>>): List<Boolean> {
		val models = engine.getTestSrc().map(::TestItemModel)
		val tasks = TestTaskModel.flatten(models, engine.getTestGroups())
		val results = ArrayList<Boolean>()
		var index = 0
		for ((taskIndex, task) in tasks.withIndex()) {
			val assigned = task.assign(raw.getOrElse(taskIndex) { emptyList() })
			for (member in task.members) {
				results.add(engine.isAnswer(index, assigned[member].orEmpty(), member.translations))
				index++
			}
		}
		return results
	}

	private fun answerFirstAndRepeat(entries: List<TestEntry>): Pair<Boolean, Boolean> {
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries)
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
		engine.setTested(1, null, 1f, entries)

		assertTrue("spaces are not part of the answer",
			engine.isAnswer(0, "  first  ", entries[0].translations()))
	}

	@Test
	fun `exact duplicate prompts merge but a shared subset stays separate`() {
		val (_, chapter) = subjectWith("c", listOf(
			"first" to listOf("same"),
			"second" to listOf("same"),
			"third" to listOf("same", "other"),
		))
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries(chapter))
		val tasks = engine.getTestGroups()

		assertEquals("only the exact duplicate pair becomes one task", 2, tasks.size)
		assertTrue("the shared subset stays a separate prompt",
			tasks.any { it.entries.size == 1 && it.entries[0].word.name == "third" })
		assertTrue("the exact duplicate pair shares one block",
			tasks.any { it.entries.size == 2 })
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
		engine.setTested(-1, null, 1f, entries)
		val names = engine.getTestSrc().map { it.word.name }

		assertTrue("the shared pair lands next to each other: $names",
			abs(names.indexOf("first") - names.indexOf("second")) == 1)
	}

	@Test
	fun `a repeat still fails after grouping moves the shared entries together`() {
		val (_, chapter) = subjectWith("c", listOf(
			"first" to listOf("same"),
			"other" to listOf("other"),
			"second" to listOf("same"),
			"last" to listOf("last"),
		))
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries(chapter))
		val picked = engine.getTestSrc()
		val firstIndex = picked.indexOfFirst { it.word.name == "first" }
		val secondIndex = picked.indexOfFirst { it.word.name == "second" }

		assertTrue(engine.isAnswer(firstIndex, "first", picked[firstIndex].translations()))
		assertFalse("the moved synonym still shares the answer block",
			engine.isAnswer(secondIndex, "first", picked[secondIndex].translations()))
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
		engine.setTested(-1, null, 1f, entries)
		val picked = engine.getTestSrc()

		assertTrue("the first different answer passes",
			engine.isAnswer(0, picked[0].word.name, picked[0].translations()))
		assertTrue("the second different answer passes",
			engine.isAnswer(1, picked[1].word.name, picked[1].translations()))
		for (pickedEntry in picked) {
			assertEquals("each synonym gets its own credit", 1,
				ItemRepository.get(pickedEntry.word.id)!!.passedTests)
			assertEquals("and no failed test", 0,
				ItemRepository.get(pickedEntry.word.id)!!.failedTests)
		}
	}

	@Test
	fun `shared prompt answers land on the member that accepts them`() {
		val entries = sharedPair("なに/なん", "なん")
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries)
		val tasks = engine.getTestGroups()

		assertEquals("the shared translation row makes one prompt", 1, tasks.size)
		assertEquals(2, tasks.single().entries.size)
		// the fields are unlabeled, so the typed order must not decide
		val results = grades(engine, listOf(listOf("なん", "なに")))

		assertEquals(2, results.count { it })
	}

	@Test
	fun `a repeated answer still fails one shared prompt member`() {
		val entries = sharedPair("なに/なん", "なん")
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries)

		val results = grades(engine, listOf(listOf("なん", "なん")))

		assertEquals("one text cannot answer two words", 1, results.count { it })
	}

	@Test
	fun `required variants may repeat across shared prompt members`() {
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = sharedPair("なに/なん", "なん")
			val engine = TestEngine()
			engine.setTested(-1, null, 1f, entries)
			val task = TestTaskModel.flatten(
				engine.getTestSrc().map(::TestItemModel), engine.getTestGroups()).single()

			assertEquals("two variants plus the shared one", 3, task.answerFields.size)
			val results = grades(engine, listOf(listOf("なん", "なに", "なん")))

			assertEquals(2, results.count { it })
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `identical variant sets may share every required answer`() {
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = sharedPair("なに/なん", "なに/なん")
			val engine = TestEngine()
			engine.setTested(-1, null, 1f, entries)

			val results = grades(engine, listOf(listOf("なん", "なに", "なん", "なに")))

			assertEquals(2, results.count { it })
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `reversed prompts do not group by a shared translation`() {
		TestEngine.applyReversed(true)
		try {
			val entries = sharedPair("なに/なん", "なん")
			val engine = TestEngine()
			engine.setTested(-1, null, 1f, entries)
			val tasks = engine.getTestGroups()

			assertEquals("different word prompts stay separate", 2, tasks.size)
			val results = grades(engine, listOf(listOf("what"), listOf("what")))
			assertEquals(2, results.count { it })
		} finally {
			TestEngine.applyReversed(false)
		}
	}

	@Test
	fun `the same variant answers unshared prompts independently`() {
		val entries = unsharedPair()
		val engine = TestEngine()
		engine.setTested(-1, null, 1f, entries)

		assertEquals("separate translation rows stay separate", 2, engine.getTestGroups().size)
		val results = grades(engine, listOf(listOf("なん"), listOf("なん")))
		assertEquals(2, results.count { it })
	}

	@Test
	fun `the answer length and each required variant set the time`() {
		val (_, chapter) = subjectWith("c", listOf("/I/You smile." to listOf("laugh")))
		assertEquals("five seconds plus 13 characters", 18, TestEngine.testTime(1f, entries(chapter)))
		TestEngine.applyRequireAllVariants(true)
		try {
			assertEquals("three fields multiply by 1.5 squared", 41, TestEngine.testTime(1f, entries(chapter)))
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `independent sides accept any complete cover instead of every combination`() {
		val (_, chapter) = subjectWith("c", listOf("a/b c/d/e" to listOf("letters")))
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val engine = TestEngine()
			engine.setTested(1, null, 1f, entries)
			val task = TestTaskModel.flatten(
				engine.getTestSrc().map(::TestItemModel), engine.getTestGroups()).single()

			assertEquals("only the widest side takes answer fields", 3, task.answerFields.size)
			assertEquals("time scales with those fields", 32, TestEngine.testTime(1f, entries))
			assertEquals(listOf(true), grades(engine, listOf(listOf("a c", "b d", "a e"))))
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `nested alternatives accept the three required sides`() {
		val (_, chapter) = subjectWith("c", listOf("a/(b 1/2)" to listOf("letters")))
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val engine = TestEngine()
			engine.setTested(1, null, 1f, entries)
			val task = TestTaskModel.flatten(
				engine.getTestSrc().map(::TestItemModel), engine.getTestGroups()).single()

			assertEquals(3, task.answerFields.size)
			assertEquals(listOf(true), grades(engine, listOf(listOf("a", "b 1", "b 2"))))
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `all variants score the word exactly once`() {
		val (_, chapter) = subjectWith("c", listOf("/I/You smile." to listOf("laugh")))
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val engine = TestEngine()
			engine.setTested(1, null, 1f, entries)

			assertTrue(engine.isAnswer(0, listOf("I smile.", "You smile.", "smile."), entries[0].translations()))
			assertEquals(1, ItemRepository.get(entries[0].word.id)!!.passedTests)
			assertEquals(0, ItemRepository.get(entries[0].word.id)!!.failedTests)
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `an incomplete variant list counts as one failed test`() {
		val (_, chapter) = subjectWith("c", listOf("/I/You smile." to listOf("laugh")))
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val engine = TestEngine()
			engine.setTested(1, null, 1f, entries)

			assertFalse(engine.isAnswer(0, "I smile.", entries[0].translations()))
			assertEquals(1, ItemRepository.get(entries[0].word.id)!!.failedTests)
			assertEquals(0, ItemRepository.get(entries[0].word.id)!!.passedTests)
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `requiring every name variant asks for the full alternative list`() {
		val (_, chapter) = subjectWith("c", listOf("/I/You smile." to listOf("laugh")))
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val engine = TestEngine()
			engine.setTested(1, null, 1f, entries)
			assertFalse("one variant alone does not suffice",
				engine.isAnswer(0, "I smile.", entries[0].translations()))
			val complete = TestEngine()
			complete.setTested(1, null, 1f, entries)
			assertTrue("every parsed variant satisfies the requirement",
				complete.isAnswer(0, listOf("I smile.", "You smile.", "smile."), entries[0].translations()))
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `requiring all reversed variants uses the full translation list`() {
		val (_, chapter) = subjectWith("c", listOf("house" to listOf("dům/domov")))
		TestEngine.applyReversed(true)
		TestEngine.applyRequireAllVariants(true)
		try {
			val entries = entries(chapter)
			val complete = TestEngine()
			complete.setTested(1, null, 1f, entries)
			assertTrue("both reversed alternatives are accepted",
				complete.isAnswer(0, listOf("dům", "domov"), entries[0].translations()))
			val partial = TestEngine()
			partial.setTested(1, null, 1f, entries)
			assertFalse("a single reversed alternative fails",
				partial.isAnswer(0, "dům", entries[0].translations()))
		} finally {
			TestEngine.applyRequireAllVariants(false)
			TestEngine.applyReversed(false)
		}
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
		engine.setTested(1, null, 1f, listOf(entry(target)))

		assertFalse("matching only one shown translation cannot pass",
			engine.isAnswer(0, "wrong", entry(target).translations()))
	}
}
