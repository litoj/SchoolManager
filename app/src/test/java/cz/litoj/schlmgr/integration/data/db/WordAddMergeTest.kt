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
 * - every create path trims the text it stores, so a spaced name is the same word.
 * - a word whose name exists in the subject scope merges into that row.
 * - an added translation that exists merges its description.
 * - a create of an unusable word is refused with nothing stored.
 * - a row without a plain home answers through its reference.
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

	/** The edge spaces of created or edited text never reach the merge lookup. */
	@Test
	fun `a spaced name is the same word - it trims before the merge`() {
		val subject = ItemRepository.createSubject(" s ", " sd ")
		val chapter = ItemRepository.createChapter(subject.id, " c ", " cd ")
		val note = ItemRepository.createNote(chapter.id, " n ", " nd ")
		val first = ItemRepository.createWord(chapter.id, " word ", " wd ", listOf(" one " to " td "))
		val translation = ItemRepository.translations(first.id).single()
		val edited = ItemRepository.createNote(chapter.id, " other ", " od ")
		ItemRepository.setDescription(edited.id, " outer ")
		val second = ItemRepository.createWord(chapter.id, "word", "second", listOf("two" to null))

		val stored: List<Triple<String, String?, String>> = listOf(
			Triple("subject name", subject.name, "s"),
			Triple("subject description", subject.description, "sd"),
			Triple("chapter name", chapter.name, "c"),
			Triple("chapter description", chapter.description, "cd"),
			Triple("note name", note.name, "n"),
			Triple("note description", note.description, "nd"),
			Triple("word name", first.name, "word"),
			Triple("word description", first.description, "wd"),
			Triple("translation name", translation.name, "one"),
			Triple("translation description", translation.description, "td"),
			Triple("description edit", ItemRepository.get(edited.id)!!.description, "outer"),
		)
		for ((label, text, trimmed) in stored) {
			assertEquals("$label stores no edge spaces", trimmed, text)
		}

		// the lookup read the trimmed names, so both creates hold one row
		assertEquals(first.id, second.id)
		assertEquals("one row carries the name", 1, ItemRepository.getByName("word", ItemKind.WORD).size)
		assertEquals(listOf("one", "two"), ItemRepository.translations(first.id).map { it.name })
	}

	/** The create door refuses an unusable word before it stores a row. */
	@Test
	fun `a create door refusal stores nothing`() {
		val subject = ItemRepository.createSubject("s", null)

		val refusals: List<Triple<String, String, () -> Unit>> = listOf(
			Triple("a blank name", "name") {
				ItemRepository.createWord(subject.id, "   ", null, listOf("trl" to null))
			},
			Triple("no translation", "translation") {
				ItemRepository.createWord(subject.id, "lonely", null, emptyList())
			},
			Triple("every translation blank", "translation") {
				ItemRepository.createWord(subject.id, "word", null, listOf("   " to null))
			},
		)

		for ((case, fragment, refuse) in refusals) {
			try {
				refuse()
				fail("$case must be refused")
			} catch (e: IllegalArgumentException) {
				assertTrue("$case: ${e.message}", fragment in e.message!!)
			}
			assertTrue("$case stored nothing", ItemRepository.children(subject.id).isEmpty())
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
		// the stored row is the more tested one, so the name merge adopts nothing
		ItemRepository.bumpCounters(listOf(ItemRepository.translations(word.id).single().id), false)
		ItemRepository.createWord(subject.id, "word", null, listOf("trl" to "second"))

		val translation = ItemRepository.translations(word.id).single()
		assertEquals("trl", translation.name)
		assertEquals("first\nsecond", translation.description)
		assertEquals("the merge keeps the more tested counters", 1, translation.failedTests)
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
