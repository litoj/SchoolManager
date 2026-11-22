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
 * - the result counts words and chapters only.
 * - the cycle guard of the reference phase adds no link for an ancestor.
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
		ItemRepository.resetForTest()
	}

	private fun item(name: String, type: ItemKind, description: String = "") =
		DbItem(name = name, description = description, type = type)

	/** A chapter whose note carries no name, beside a word the save would accept. */
	private fun unnamedItem(): List<StreamItem> {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("word", ItemKind.WORD), chapter)
		return listOf(chapter, StreamItem(item("  ", ItemKind.NOTE), chapter), word,
			StreamItem(item("translation", ItemKind.TRANSLATION), word))
	}

	/** A chapter holding a word that has no translation at all. */
	private fun wordWithoutTranslation(): List<StreamItem> {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		return listOf(chapter, StreamItem(item("word", ItemKind.WORD), chapter))
	}

	/** A word holding a note beside its translation. */
	private fun childThatIsNoTranslation(): List<StreamItem> {
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("word", ItemKind.WORD), chapter)
		return listOf(chapter, word, StreamItem(item("t", ItemKind.TRANSLATION), word),
			StreamItem(item("n", ItemKind.NOTE), word))
	}

	@Test
	fun `a reference child takes the imported content like its own row`() {
		val subject = ItemRepository.createSubject("S", null)
		val home = ItemRepository.createChapter(subject.id, "home", null)
		val holder = ItemRepository.createChapter(subject.id, "holder", null)
		val chapter = ItemRepository.createChapter(home.id, "c1", null)
		assertTrue(ItemRepository.reference(chapter.id, holder.id))

		val plain = StreamItem(item("c1", ItemKind.CHAPTER), null)
		val word = StreamItem(item("w", ItemKind.WORD), plain)
		val trl = StreamItem(item("t", ItemKind.TRANSLATION), word)
		ItemRepository.import(object : DataImporter {
			override val items = listOf(plain, word, trl)
			override val references: List<StreamReference> = emptyList()
		}, holder.id)

		// the reference is the same row as the plain child, so no copy appears beside it
		val kids = ItemRepository.children(holder.id).filter { it.name == "c1" }
		assertEquals(1, kids.size)
		assertTrue(ItemRepository.linkIsReference(holder.id, chapter.id))
		assertEquals(listOf("w"), ItemRepository.children(chapter.id).map { it.name })
	}

	@Test
	fun `a stream that fails its pre-check rolls the whole import back`() {
		val refusals = mapOf(
			"an item without a name" to (unnamedItem() to "without a name"),
			"a word without a translation" to (wordWithoutTranslation() to "A word without a translation"),
			"a child that is no translation" to (childThatIsNoTranslation() to "its translations"),
		)

		for ((name, refusal) in refusals) {
			val (rows, reason) = refusal
			try {
				ItemRepository.import(object : DataImporter {
					override val items = rows
					override val references: List<StreamReference> = emptyList()
				})
				fail("$name must fail the import")
			} catch (e: IllegalArgumentException) {
				assertTrue("$name refused for the wrong reason", e.message!!.contains(reason))
			}
			// the pre-check runs before the transaction, so not even the chapter lands
			assertEquals("$name wrote a subject", emptyList<String>(), ItemRepository.subjects().map { it.name })
		}
	}

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

		// the stream counts its own rows, one word and one chapter
		assertEquals(1, result.words)
		assertEquals(1, result.chapters)

		// the null-parent chapter landed as a subject of the root
		val subject = ItemRepository.subjects().single()
		assertEquals("chapter", subject.name)

		val children = ItemRepository.children(subject.id)
		assertEquals(listOf("word", "note"), children.map { it.name })
		assertEquals("word description", children[0].description)
		assertEquals("note description", children[1].description)
		assertEquals(listOf("translation"), ItemRepository.translations(children[0].id).map { it.name })
	}

	@Test
	fun `a bare root word does not merge into a subject's word`() {
		val subject = ItemRepository.createSubject("english", null)
		ItemRepository.createWord(subject.id, "w", null, listOf("t" to null))

		// a root-anchored stream merges its top-level chapter into the subject, so
		// the bare word of the same name must stay the own row of the root
		val chapter = StreamItem(item("chapter", ItemKind.CHAPTER), null)
		val word = StreamItem(item("w", ItemKind.WORD), null)
		val trl = StreamItem(item("t2", ItemKind.TRANSLATION), word)
		ItemRepository.import(object : DataImporter {
			override val items = listOf(chapter, word, trl)
			override val references: List<StreamReference> = emptyList()
		})

		assertEquals(1, ItemRepository.children(ROOT_ID).count { it.name == "w" })
		// the word of the subject kept its own translation, not the one of the bare word
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

		// a word of the file with the same name merges onto the existing row with
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
	fun `a stream reference to its own ancestor or to itself is skipped`() {
		val subject = ItemRepository.createSubject("s", null)
		val c = ItemRepository.createChapter(subject.id, "c", null)
		val d = ItemRepository.createChapter(c.id, "d", null)

		// the stream re-imports the existing tree so the rows merge onto their real
		// rows, and its references ask d for its ancestor c and c for itself
		val root = StreamItem(item("s", ItemKind.CHAPTER), null)
		val chapterC = StreamItem(item("c", ItemKind.CHAPTER), root)
		val chapterD = StreamItem(item("d", ItemKind.CHAPTER), chapterC)
		val result = ItemRepository.import(object : DataImporter {
			override val items = listOf(root, chapterC, chapterD)
			override val references = listOf(
				StreamReference(chapterD, listOf("s"), "c"),
				StreamReference(chapterC, listOf("s"), "c"),
			)
		})

		assertEquals(subject.id, result.topLevel.single().id)
		// neither guard adds a link, so c keeps the subject as its only parent and
		// the tree stays a tree for the export walk
		assertFalse(ItemRepository.linkIsReference(d.id, c.id))
		assertFalse(ItemRepository.linkIsReference(c.id, c.id))
		assertEquals(listOf(subject.id), ItemRepository.parentIds(c.id))
	}
}
