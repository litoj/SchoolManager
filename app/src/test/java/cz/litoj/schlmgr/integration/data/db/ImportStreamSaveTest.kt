package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The save phase of a hand-made item stream ([ItemRepository.import]):
 * chapters, words, translations and notes each land as the right row shape,
 * and the result counts words and chapters only. Pins the per-kind save
 * behaviour, notes included (the tree JSON drops them, so only a direct
 * stream or a word list carries them), and the reference phase's cycle
 * guard (an ancestor reference adds no link).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportStreamSaveTest {

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

	private fun item(name: String, type: ItemKind, description: String = "") =
		DbItem(name = name, description = description, type = type)

	@Test
	fun `a stream with every kind saves each row in its place and counts words and chapters`() {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("word", ItemKind.WORD, "word description"), chapter)
		val translation = StreamItem(item("translation", ItemKind.TRANSLATION), word)
		val note = StreamItem(item("note", ItemKind.NOTE, "note description"), chapter)
		val result = ItemRepository.import(object : DataImporter {
			override val items = listOf(chapter, word, translation, note)
			override val references: List<StreamReference> = emptyList()
		})

		// the stream counts its own rows — one word, one chapter
		assertEquals(1, result.words)
		assertEquals(1, result.chapters)

		// the null-parent chapter landed as a subject of the root
		val subject = ItemRepository.subjects().single()
		assertEquals("chapter", subject.name)

		// the word and the note both landed under it, the translation under the word
		val children = ItemRepository.children(subject.id)
		assertEquals(listOf("word", "note"), children.map { it.name })
		assertEquals("word description", children[0].description)
		assertEquals("note description", children[1].description)
		assertEquals(listOf("translation"), ItemRepository.translations(children[0].id).map { it.name })
	}

	@Test
	fun `a word without a translation fails the whole import with nothing written`() {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("word", ItemKind.WORD), chapter)
		try {
			ItemRepository.import(object : DataImporter {
				override val items = listOf(chapter, word)
				override val references: List<StreamReference> = emptyList()
			})
			fail("a translation-less word must fail the import")
		} catch (e: IllegalArgumentException) {
			// the message names the offending word
			assertTrue(e.message!!.contains("word"))
		}
		// the transaction rolled back — not even the chapter landed
		assertTrue(ItemRepository.subjects().isEmpty())
	}

	@Test
	fun `a non-translation child under a word fails the whole import`() {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("word", ItemKind.WORD), chapter)
		val translation = StreamItem(item("t", ItemKind.TRANSLATION), word)
		val note = StreamItem(item("n", ItemKind.NOTE), word)
		try {
			ItemRepository.import(object : DataImporter {
				override val items = listOf(chapter, word, translation, note)
				override val references: List<StreamReference> = emptyList()
			})
			fail("a note under a word must fail the import")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("translations"))
		}
		assertTrue(ItemRepository.subjects().isEmpty())
	}

	@Test
	fun `a bare root word does not merge into a subject's word`() {
		val subject = ItemRepository.createSubject("english", null)
		ItemRepository.createWord(subject.id, "w", null, listOf("t" to null))

		// a root-anchored stream — its top-level chapter merges into the
		// subject (loading its scope), and the bare word of the same name
		// must stay the root's own row — the root scope is its direct
		// children, not every subject
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("w", ItemKind.WORD), null)
		val trl = StreamItem(item("t2", ItemKind.TRANSLATION), word)
		ItemRepository.import(object : DataImporter {
			override val items = listOf(chapter, word, trl)
			override val references: List<StreamReference> = emptyList()
		})

		// the bare word landed as its own root row
		assertEquals(1, ItemRepository.children(ROOT_ID).count { it.name == "w" })
		// the subject's word kept its own translation, not the bare word's
		val subjectWord = ItemRepository.children(subject.id).single { it.type == ItemKind.WORD }
		assertEquals(listOf("t"), ItemRepository.translations(subjectWord.id).map { it.name })
	}

	@Test
	fun `an import adopting a word's counters refreshes the sibling chapter holding it`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val existing = ItemRepository.createWord(c2.id, "w", null, listOf("t" to null))
		// the answer bumps the word and its chapter path together
		repeat(2) { ItemRepository.bumpCounters(listOf(existing.id, c2.id), false) }
		assertEquals(2, ItemRepository.get(c2.id)!!.failedTests)

		// a file's word of the same name merges onto the existing row with
		// more tests, anchored at the sibling chapter
		val word = StreamItem(
			DbItem(name = "w", description = "", type = ItemKind.WORD, passedTests = 9), null)
		val trl = StreamItem(item("t2", ItemKind.TRANSLATION), word)
		ItemRepository.import(object : DataImporter {
			override val items = listOf(word, trl)
			override val references: List<StreamReference> = emptyList()
		}, anchorId = c1.id)

		assertEquals("the more-tested file counters adopted", 9, ItemRepository.getByName("w", ItemKind.WORD).single().passedTests)
		// the sibling chapter holding the merged word refreshed its derivation
		assertEquals(9, ItemRepository.get(c2.id)!!.passedTests)
		assertEquals(0, ItemRepository.get(c2.id)!!.failedTests)
	}

	@Test
	fun `a stream reference to an ancestor of its own parent is skipped`() {
		val subject = ItemRepository.createSubject("s", null)
		val c = ItemRepository.createChapter(subject.id, "c", null)
		val d = ItemRepository.createChapter(c.id, "d", null)

		// the stream re-imports the existing tree so the rows merge onto
		// their real rows, and its reference asks d to link its own ancestor c
		val root = StreamItem(item("s", ItemKind.CHAPTER), null)
		val chapterC = StreamItem(item("c", ItemKind.CHAPTER), root)
		val chapterD = StreamItem(item("d", ItemKind.CHAPTER), chapterC)
		val result = ItemRepository.import(object : DataImporter {
			override val items = listOf(root, chapterC, chapterD)
			override val references = listOf(StreamReference(chapterD, listOf("s"), "c"))
		})

		// the import succeeded — the root item merged onto the subject
		assertEquals(subject.id, result.topLevel.single().id)
		// the ancestor reference added no d→c link — it is no reference, and
		// c keeps the subject as its only parent, so the tree stays a tree
		assertFalse(ItemRepository.linkIsReference(d.id, c.id))
		assertEquals(listOf(subject.id), ItemRepository.parentIds(c.id))
	}

	@Test
	fun `a stream reference onto the referencing row itself is skipped`() {
		val subject = ItemRepository.createSubject("s", null)
		val c = ItemRepository.createChapter(subject.id, "c", null)

		// the self case — the reference resolves to d itself (a self-loop the
		// export walk could never leave)
		val root = StreamItem(item("s", ItemKind.CHAPTER), null)
		val chapterC = StreamItem(item("c", ItemKind.CHAPTER), root)
		ItemRepository.import(object : DataImporter {
			override val items = listOf(root, chapterC)
			override val references = listOf(StreamReference(chapterC, listOf("s"), "c"))
		})

		assertFalse(ItemRepository.linkIsReference(c.id, c.id))
		assertEquals(listOf(subject.id), ItemRepository.parentIds(c.id))
	}
}
