package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
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
 * The import/export data equivalency of both file formats, through the
 * repository's stream boundary — a graph exported and re-imported into a
 * fresh database must come back with the same names, descriptions, order
 * and translations — everything its format carries — and the formats'
 * documented losses must be exactly the documented ones (the word list
 * drops the success counters while only the tree JSON drops notes). The
 * unified deduplication holds for every format — re-importing the same
 * text adds nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportExportEquivalencyTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		val app = RuntimeEnvironment.getApplication()
		GlobalDependencies.appContext = app
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
	}

	/** Closes the export-side database and installs a fresh empty one. */
	private fun freshDatabase() {
		db.close()
		val app = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	/**
	 * The source graph — a subject with a nested chapter, words with
	 * descriptions and translations, differing counters, and a note the
	 * JSON format drops and the word list carries.
	 */
	private fun buildGraph(): DbItem {
		val subject = ItemRepository.createSubject("Deutsch", "subject description")
		val chapter = ItemRepository.createChapter(subject.id, "Lektion 1", "chapter description")
		ItemRepository.createNote(subject.id, "a note", "notes are not exported")
		ItemRepository.createWord(chapter.id, "das Haus", "noun", listOf("house" to "dwell", "home" to null))
		ItemRepository.createWord(chapter.id, "die Schule", null, listOf("school" to null))
		// counters the JSON format carries and the word list drops
		val haus = ItemRepository.getByName("das Haus", ItemKind.WORD).single()
		repeat(3) { ItemRepository.bumpCounters(listOf(haus.id), true) }
		ItemRepository.bumpCounters(listOf(haus.id), false)
		return subject
	}

	@Test
	fun `the tree JSON round trip preserves names descriptions order translations and counters`() {
		val subject = buildGraph()
		val text = ItemRepository.export(HJsonExporter(), listOf(subject))

		freshDatabase()
		val result = ItemRepository.import(HJsonImporter(text))

		// the stream counts its own rows — the subject and the nested chapter
		assertEquals(2, result.words)
		assertEquals(2, result.chapters)
		val back = ItemRepository.subjects().single()
		assertEquals("Deutsch", back.name)
		assertEquals("subject description", back.description)
		// the note is a documented loss of the format
		val children = ItemRepository.children(back.id)
		assertEquals(listOf("Lektion 1"), children.map { it.name })
		val chapter = children.single()
		assertEquals("chapter description", chapter.description)
		val words = ItemRepository.children(chapter.id)
		assertEquals(listOf("das Haus", "die Schule"), words.map { it.name })
		val haus = words[0]
		assertEquals("noun", haus.description)
		// the counters came through the file
		assertEquals(3, haus.passedTests)
		assertEquals(1, haus.failedTests)
		assertEquals(listOf("house", "home"), ItemRepository.translations(haus.id).map { it.name })
		assertEquals("dwell", ItemRepository.translations(haus.id)[0].description)
	}

	@Test
	fun `the word-list round trip preserves names descriptions translations and notes, drops counters`() {
		val subject = buildGraph()
		val text = ItemRepository.export(WordListExporter(), listOf(subject))

		freshDatabase()
		// the export wrapper of a subject import IS the subject — the stream's
		// root item lands under the root anchor
		val result = ItemRepository.import(WordListImporter("Deutsch", text))

		assertEquals(2, result.words)
		// the stream counts its own rows — the wrapper and the nested chapter
		assertEquals(2, result.chapters)
		val back = ItemRepository.subjects().single()
		assertEquals("Deutsch", back.name)
		assertEquals("subject description", back.description)
		// the note and the chapter, in the file's own order — the writer
		// emits the stream as it came, the importer keeps that order
		val children = ItemRepository.children(back.id)
		assertEquals(listOf("Lektion 1", "a note"), children.map { it.name })
		val chapter = children[0]
		assertEquals("chapter description", chapter.description)
		val note = children[1]
		assertEquals("notes are not exported", note.description)
		assertEquals(ItemKind.NOTE, note.type)
		val words = ItemRepository.children(chapter.id)
		assertEquals(listOf("das Haus", "die Schule"), words.map { it.name })
		val haus = words[0]
		assertEquals("noun", haus.description)
		// the counters are a documented loss of the format
		assertEquals(0, haus.passedTests)
		assertEquals(0, haus.failedTests)
		assertEquals(listOf("house", "home"), ItemRepository.translations(haus.id).map { it.name })
	}

	@Test
	fun `the stream boundaries report every row they pass`() {
		val subject = buildGraph()
		var walked = 0
		val text = ItemRepository.export(
			object : DataExporter by HJsonExporter() {
				override fun onItem(item: StreamItem) {
					walked++
				}
			}, listOf(subject))
		// the walk reports the whole stream, the note included — the subject, the
		// nested chapter, two words, three translations, the note — the format
		// drops the note only at the render, so the re-import saves 7 of the 8
		assertEquals(8, walked)

		freshDatabase()
		var saved = 0
		ItemRepository.import(object : DataImporter by HJsonImporter(text) {
			override fun onItem(entry: StreamItem) {
				saved++
			}
		})
		assertEquals(7, saved)
	}

	@Test
	fun `re-importing the same JSON text adds nothing`() {
		val text = ItemRepository.export(HJsonExporter(), listOf(buildGraph()))

		freshDatabase()
		ItemRepository.import(HJsonImporter(text))
		ItemRepository.import(HJsonImporter(text))

		assertEquals(1, ItemRepository.subjects().size)
		val subject = ItemRepository.subjects().single()
		val chapter = ItemRepository.children(subject.id).single()
		assertEquals(2, ItemRepository.children(chapter.id).size)
		assertEquals(2, ItemRepository.translations(
			ItemRepository.children(chapter.id)[0].id).size)
	}

	@Test
	fun `re-importing the same word-list text adds nothing`() {
		val text = ItemRepository.export(WordListExporter(), listOf(buildGraph()))

		freshDatabase()
		ItemRepository.import(WordListImporter("Deutsch", text))
		ItemRepository.import(WordListImporter("Deutsch", text))

		// the unified deduplication — the subject merges with itself, the
		// chapters and notes under the parent merge — nothing doubles
		assertEquals(1, ItemRepository.subjects().size)
		val subject = ItemRepository.subjects().single()
		assertEquals(listOf("Lektion 1", "a note"), ItemRepository.children(subject.id).map { it.name })
		val chapter = ItemRepository.children(subject.id).first { it.name == "Lektion 1" }
		assertEquals(2, ItemRepository.children(chapter.id).size)
	}

	@Test
	fun `an import at the root keeps each top-level chapter its own subject`() {
		val text = """
			[
			  {
			    name: S1
			    words:
			    [
			      {
			        name: same
			        translations:
			        [
			          {
			            name: t1
			          }
			        ]
			      }
			    ]
			  }
			  {
			    name: S2
			    words:
			    [
			      {
			        name: same
			        translations:
			        [
			          {
			            name: t2
			          }
			        ]
			      }
			    ]
			  }
			]
		""".trimIndent()

		ItemRepository.import(HJsonImporter(text))

		val subjects = ItemRepository.subjects()
		assertEquals(listOf("S1", "S2"), subjects.map { it.name })
		val w1 = ItemRepository.children(subjects[0].id).single()
		val w2 = ItemRepository.children(subjects[1].id).single()
		// the same name in two subjects is two rows — the uniqueness the merge
		// checks is the subject's own contents only
		assertTrue(w1.id != w2.id)
		assertEquals(listOf("t1"), ItemRepository.translations(w1.id).map { it.name })
		assertEquals(listOf("t2"), ItemRepository.translations(w2.id).map { it.name })
	}

	@Test
	fun `a root import joins only the same-named subject's rows`() {
		val a = ItemRepository.createSubject("A", null)
		val b = ItemRepository.createSubject("B", null)
		val wa = ItemRepository.createWord(a.id, "w", null, listOf("a-trs" to null))
		val wb = ItemRepository.createWord(b.id, "w", null, listOf("b-trs" to null))
		ItemRepository.bumpCounters(listOf(wa.id), true)

		// the file's top-level chapter joins subject A — its word merges into
		// A's row and never B's same-named one
		val text = """
			{
			  name: A
			  words:
			  [
			    {
			      name: w
			      translations:
			      [
			        {
			          name: new-trs
			        }
			      ]
			    }
			  ]
			}
		""".trimIndent()
		ItemRepository.import(HJsonImporter(text))

		// still the two subjects — the file's chapter merged into A
		assertEquals(listOf("A", "B"), ItemRepository.subjects().map { it.name })
		// A's row kept its counters and gained the file's translation
		assertEquals(1, ItemRepository.get(wa.id)!!.passedTests)
		assertEquals(listOf("a-trs", "new-trs"), ItemRepository.translations(wa.id).map { it.name })
		// B's row is untouched — the merge never reached it
		assertEquals(0, ItemRepository.get(wb.id)!!.passedTests)
		assertEquals(listOf("b-trs"), ItemRepository.translations(wb.id).map { it.name })
	}

	@Test
	fun `a root import adopts the counters of a more-tested word`() {
		val a = ItemRepository.createSubject("A", null)
		val w = ItemRepository.createWord(a.id, "w", null, listOf("old-trs" to null))
		ItemRepository.bumpCounters(listOf(w.id), true)

		// the file's word ran more tests — its counters win the merge
		val text = """
			{
			  name: A
			  words:
			  [
			    {
			      name: w
			      s: 4
			      f: 1
			      translations:
			      [
			        {
			          name: new-trs
			        }
			      ]
			    }
			  ]
			}
		""".trimIndent()
		ItemRepository.import(HJsonImporter(text))

		val merged = ItemRepository.get(w.id)!!
		assertEquals(4, merged.passedTests)
		assertEquals(1, merged.failedTests)
		assertEquals(listOf("old-trs", "new-trs"), ItemRepository.translations(w.id).map { it.name })
	}
}
