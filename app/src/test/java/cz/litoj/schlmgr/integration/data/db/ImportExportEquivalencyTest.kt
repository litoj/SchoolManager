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
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the word list drops the success counters and the reference links.
 * - the tree JSON keeps both.
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
		ItemRepository.resetForTest()
	}

	private fun freshDatabase() {
		db.close()
		ItemRepository.resetForTest()
		val app = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	private fun buildGraph(): DbItem {
		val subject = ItemRepository.createSubject("Deutsch", "subject description")
		val chapter = ItemRepository.createChapter(subject.id, "Lektion 1", "chapter description")
		val second = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		ItemRepository.createNote(subject.id, "a note", "note description")
		ItemRepository.createWord(chapter.id, "das Haus", "noun", listOf("house" to "dwell", "home" to null))
		ItemRepository.createWord(chapter.id, "die Schule", null, listOf("school" to null))
		val haus = ItemRepository.getByName("das Haus", ItemKind.WORD).single()
		assertTrue(ItemRepository.reference(haus.id, second.id))
		// counters the JSON format carries and the word list drops
		repeat(3) { ItemRepository.bumpCounters(listOf(haus.id), true) }
		ItemRepository.bumpCounters(listOf(haus.id), false)
		return subject
	}

	@Test
	fun `the tree JSON round trip preserves names descriptions order translations counters notes and references`() {
		val subject = buildGraph()
		// the children of a subject interleave, so the cycle proves the file order
		ItemRepository.createWord(subject.id, "w1", null, listOf("t" to null))
		ItemRepository.createChapter(subject.id, "c1", null)
		ItemRepository.createWord(subject.id, "w2", null, listOf("t" to null))
		ItemRepository.createNote(subject.id, "n1", null)
		ItemRepository.createChapter(subject.id, "c2", null)
		val text = ItemRepository.export(HJsonExporter(), listOf(subject))

		freshDatabase()
		val result = ItemRepository.import(HJsonImporter(text))

		// the stream counts its own rows, a note is neither of them
		assertEquals(4, result.words)
		assertEquals(5, result.chapters)
		val back = ItemRepository.subjects().single()
		assertEquals("Deutsch", back.name)
		assertEquals("subject description", back.description)
		val children = ItemRepository.children(back.id)
		assertEquals(listOf("Lektion 1", "Lektion 2", "a note", "w1", "c1", "w2", "n1", "c2"),
			children.map { it.name })
		val chapter = children[0]
		assertEquals("chapter description", chapter.description)
		val words = ItemRepository.children(chapter.id)
		assertEquals(listOf("das Haus", "die Schule"), words.map { it.name })
		val haus = words[0]
		assertEquals("noun", haus.description)
		assertEquals(3, haus.passedTests)
		assertEquals(1, haus.failedTests)
		assertEquals(listOf("house", "home"), ItemRepository.translations(haus.id).map { it.name })
		assertEquals("dwell", ItemRepository.translations(haus.id)[0].description)
		val note = children[2]
		assertEquals(ItemKind.NOTE, note.type)
		assertEquals("note description", note.description)
		val second = children[1]
		assertTrue(ItemRepository.linkIsReference(second.id, haus.id))
	}

	@Test
	fun `a reference across two exported subjects resolves through the named root`() {
		val first = ItemRepository.createSubject("First", null)
		val word = ItemRepository.createWord(first.id, "w", null, listOf("t" to null))
		val second = ItemRepository.createSubject("Second", null)
		val holder = ItemRepository.createChapter(second.id, "holder", null)
		assertTrue(ItemRepository.reference(word.id, holder.id))

		// the subject of the holder listed first, so the path of the reference
		// still names the root of the word
		val text = ItemRepository.export(HJsonExporter(), listOf(second, first))

		freshDatabase()
		ItemRepository.import(HJsonImporter(text))

		val subjects = ItemRepository.subjects()
		assertEquals(listOf("Second", "First"), subjects.map { it.name })
		val backWord = ItemRepository.children(subjects[1].id).single()
		assertEquals(listOf("t"), ItemRepository.translations(backWord.id).map { it.name })
		val backHolder = ItemRepository.children(subjects[0].id).single()
		assertTrue(ItemRepository.linkIsReference(backHolder.id, backWord.id))
	}

	@Test
	fun `the word-list round trip preserves names descriptions translations and notes, drops counters and references`() {
		val subject = buildGraph()
		val text = ItemRepository.export(WordListExporter(), listOf(subject))

		freshDatabase()
		// the export wrapper of a subject import IS the subject, so the root item
		// of the stream lands under the root anchor
		val result = ItemRepository.import(WordListImporter("Deutsch", text))

		assertEquals(2, result.words)
		// the stream counts its own rows, the wrapper and the two chapters
		assertEquals(3, result.chapters)
		val back = ItemRepository.subjects().single()
		assertEquals("Deutsch", back.name)
		assertEquals("subject description", back.description)
		// the chapters and the note in the order of the file, because the writer
		// emits the stream as it came and the importer keeps that order
		val children = ItemRepository.children(back.id)
		assertEquals(listOf("Lektion 1", "Lektion 2", "a note"), children.map { it.name })
		val chapter = children[0]
		assertEquals("chapter description", chapter.description)
		val note = children[2]
		assertEquals("note description", note.description)
		assertEquals(ItemKind.NOTE, note.type)
		val words = ItemRepository.children(chapter.id)
		assertEquals(listOf("das Haus", "die Schule"), words.map { it.name })
		val haus = words[0]
		assertEquals("noun", haus.description)
		// the counters are a documented loss of the format
		assertEquals(0, haus.passedTests)
		assertEquals(0, haus.failedTests)
		assertEquals(listOf("house", "home"), ItemRepository.translations(haus.id).map { it.name })
		// the reference link is a documented loss too, so the second chapter
		// comes back with no content of its own
		assertEquals(0, ItemRepository.children(children[1].id).size)
	}

	@Test
	fun `the stream boundaries report every row they pass`() {
		val subject = buildGraph()
		var walked = 0
		val text = ItemRepository.export(
			object : DataExporter by HJsonExporter() {
				override fun onStream(items: List<StreamItem>) {
					walked = items.size
				}
			}, listOf(subject))
		// the handover carries the whole stream: the subject, two chapters, two
		// words, three translations, the note and the reference child
		assertEquals(10, walked)

		freshDatabase()
		var saved = 0
		ItemRepository.import(object : DataImporter by HJsonImporter(text) {
			override fun onItem(entry: StreamItem) {
				saved++
			}
		})
		// the reference entry links an existing row, so it saves none
		assertEquals(9, saved)
	}

	@Test
	fun `re-importing the same text adds nothing`() {
		val graph = buildGraph()
		val json = ItemRepository.export(HJsonExporter(), listOf(graph))
		val wordList = ItemRepository.export(WordListExporter(), listOf(graph))
		val files: Map<String, List<DataImporter>> = mapOf(
			"tree JSON" to listOf(HJsonImporter(json), HJsonImporter(json)),
			"word list" to listOf(
				WordListImporter("Deutsch", wordList), WordListImporter("Deutsch", wordList)),
		)

		for ((name, twice) in files) {
			freshDatabase()
			for (file in twice)
				ItemRepository.import(file)

			// the deduplication merges the subject with itself and every row under
			// its own parent, so nothing doubles
			assertEquals("$name doubled its subjects", 1, ItemRepository.subjects().size)
			val subject = ItemRepository.subjects().single()
			assertEquals("$name doubled its children", listOf("Lektion 1", "Lektion 2", "a note"),
				ItemRepository.children(subject.id).map { it.name })
			val chapter = ItemRepository.children(subject.id).first { it.name == "Lektion 1" }
			assertEquals("$name doubled its words", 2, ItemRepository.children(chapter.id).size)
			val word = ItemRepository.children(chapter.id).first { it.name == "das Haus" }
			assertEquals("$name doubled its translations", 2, ItemRepository.translations(word.id).size)
		}
	}

	@Test
	fun `a root import joins its own subject and leaves the others their rows`() {
		val a = ItemRepository.createSubject("A", null)
		val b = ItemRepository.createSubject("B", null)
		val wa = ItemRepository.createWord(a.id, "w", null, listOf("a-trs" to null))
		val wb = ItemRepository.createWord(b.id, "w", null, listOf("b-trs" to null))
		ItemRepository.bumpCounters(listOf(wa.id), true)

		// the top-level chapter named A joins subject A, the chapter named C is a
		// subject of its own, so the same word name lands as two rows
		val text = """
			[
			  {
			    name: A
			    children:
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
			  {
			    name: C
			    children:
			    [
			      {
			        name: w
			        translations:
			        [
			          {
			            name: c-trs
			          }
			        ]
			      }
			    ]
			  }
			]
		""".trimIndent()
		ItemRepository.import(HJsonImporter(text))

		assertEquals(listOf("A", "B", "C"), ItemRepository.subjects().map { it.name })
		// the merge checks uniqueness only within the subject
		val c = ItemRepository.subjects().single { it.name == "C" }
		val wc = ItemRepository.children(c.id).single()
		assertTrue(wa.id != wc.id)
		// the row of A kept its counters and gained the translation of the file
		assertEquals(1, ItemRepository.get(wa.id)!!.passedTests)
		assertEquals(listOf("a-trs", "new-trs"), ItemRepository.translations(wa.id).map { it.name })
		assertEquals(listOf("c-trs"), ItemRepository.translations(wc.id).map { it.name })
		// the row of B is untouched, because the merge never reached it
		assertEquals(0, ItemRepository.get(wb.id)!!.passedTests)
		assertEquals(listOf("b-trs"), ItemRepository.translations(wb.id).map { it.name })
	}

	@Test
	fun `a mixed file lands as it is at the root or in the open container`() {
		val subject = ItemRepository.createSubject("S", null)
		val box = ItemRepository.createChapter(subject.id, "box", null)

		// the file is a list of entries, so a word, a note and a chapter land
		// where the import points, in the order of the file
		val text = """
			[
			  {
			    name: w
			    translations:
			    [
			      {
			        name: t
			      }
			    ]
			  }
			  {
			    name: note
			    desc: nd
			  }
			  {
			    name: inner
			    children: []
			  }
			]
		""".trimIndent()
		val places = mapOf(
			"the root" to (ROOT_ID to ItemRepository.children(ROOT_ID).size),
			"the open container" to (box.id to ItemRepository.children(box.id).size),
		)

		for ((name, place) in places) {
			ItemRepository.import(HJsonImporter(text), place.first)
			// no subject wraps the content, the entries land as the rows of the target
			val landed = ItemRepository.children(place.first).drop(place.second)
			assertEquals("$name lost a row", listOf("w", "note", "inner"), landed.map { it.name })
			assertEquals("$name changed a kind", listOf(ItemKind.WORD, ItemKind.NOTE, ItemKind.CHAPTER),
				landed.map { it.type })
			assertEquals("$name lost the note description", "nd", landed[1].description)
			assertEquals("$name lost the translation", listOf("t"),
				ItemRepository.translations(landed[0].id).map { it.name })
		}

		assertEquals(listOf("box"), ItemRepository.children(subject.id).map { it.name })
	}

	@Test
	fun `a reference at the top level is rejected`() {
		// a reference names a target for a parent chapter of the file, while the
		// top level itself is no parent
		val error = assertThrows(IllegalArgumentException::class.java) {
			HJsonImporter("[\n  {\n    ref: [\"S\", \"w\"]\n  }\n]")
		}

		assertTrue(error.message!!.contains("a reference needs a parent chapter"))
	}
}
