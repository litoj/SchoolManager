package cz.litoj.schlmgr.integration.data.transfer

import android.net.Uri
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - the export bytes come from the real [ExportPipeline].
 * - the import dispatch comes from the real [ImporterFactory].
 * - the database carries every row kind and the name shapes that stress a codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportImportWorkerRoundTripTest {

	private lateinit var db: ItemDatabase
	private var nextPick = 0

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

	private fun freshDatabase() {
		db.close()
		val app = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	private fun linkCount(): Int = db.openHelper.readableDatabase
		.query("SELECT COUNT(*) FROM item_link").use { cursor ->
			cursor.moveToFirst()
			cursor.getInt(0)
		}

	private fun exportPicked(format: ExportFormat, selected: () -> List<DbItem>): String {
		val uri = Uri.parse("content://schlmgr.test/out/${nextPick++}")
		val out = ByteArrayOutputStream()
		shadowOf(RuntimeEnvironment.getApplication().contentResolver)
			.registerOutputStream(uri, out)
		ExportPipeline(GlobalDependencies.appContext, { it.run() }, uri, format, selected) {}
			.start().join()
		return String(out.toByteArray(), StandardCharsets.UTF_8)
	}

	private fun importPicked(fileName: String, open: () -> DbItem?, text: String) {
		val uri = Uri.parse("content://schlmgr.test/pick/${nextPick++}")
		val bytes = text.toByteArray(StandardCharsets.UTF_8)
		shadowOf(RuntimeEnvironment.getApplication().contentResolver)
			.registerInputStream(uri, object : java.io.ByteArrayInputStream(bytes) {
				override fun close() { reset() }
			})
		ImporterFactory.create(
			GlobalDependencies.appContext, { it.run() }, uri, fileName,
			FileGateway.startsAsJson(GlobalDependencies.appContext, uri),
			ImporterFactory.targetFor(open()), open, { open() == null },
			{}, {}, {},
		).start().join()
	}

	private fun buildStressDb() {
		val mathe = ItemRepository.createSubject("Mathe", "subject desc")
		val c0 = ItemRepository.createChapter(mathe.id, "1. 100", "chapter desc")
		val c1 = ItemRepository.createChapter(mathe.id, "c: one", null)
		val w1 = ItemRepository.createWord(c0.id, "Hund, der", "noun, big",
			listOf("dog" to "a: dog", "hound #2" to null))
		ItemRepository.createWord(c0.id, "[bracket] word", null, listOf("x [y]" to null))
		ItemRepository.createNote(c0.id, "note, with comma", "desc")
		val rootWord = ItemRepository.createWord(mathe.id, "root word", null, listOf("rw" to null))
		ItemRepository.bumpWordCounters(w1.id, true)
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val dc = ItemRepository.createChapter(deutsch.id, "Lektion", null)
		ItemRepository.createWord(dc.id, "das Haus", null, listOf("house" to null))
		assertTrue(ItemRepository.reference(w1.id, c1.id))
		assertTrue(ItemRepository.reference(c0.id, c1.id))
		assertTrue(ItemRepository.reference(w1.id, dc.id))
	}

	/** The shape of the two saved subjects: names in order, kinds, links. */
	private fun assertStressTree(mathe: DbItem, deutsch: DbItem) {
		assertEquals("Mathe", mathe.name)
		assertEquals("subject desc", mathe.description)
		val children = ItemRepository.children(mathe.id)
		assertEquals(listOf("1. 100", "c: one", "root word"), children.map { it.name })
		val c0 = children[0]
		val c1 = children[1]
		assertEquals("chapter desc", c0.description)
		val c0Children = ItemRepository.children(c0.id)
		assertEquals(listOf("Hund, der", "[bracket] word", "note, with comma"), c0Children.map { it.name })
		val w1 = c0Children[0]
		assertEquals("noun, big", w1.description)
		assertEquals(1, w1.passedTests)
		assertEquals(listOf("dog", "hound #2"), ItemRepository.translations(w1.id).map { it.name })
		assertEquals("a: dog", ItemRepository.translations(w1.id)[0].description)
		assertEquals(ItemKind.NOTE, c0Children[2].type)
		assertTrue(ItemRepository.linkIsReference(c1.id, w1.id))
		assertTrue(ItemRepository.linkIsReference(c1.id, c0.id))
		val dc = ItemRepository.children(deutsch.id).single()
		assertTrue(ItemRepository.linkIsReference(dc.id, w1.id))
	}

	@Test
	fun `the root export imports at the root through the workers`() {
		buildStressDb()
		val links = linkCount()
		val text = exportPicked(ExportFormat.JSON) { ItemRepository.subjects() }

		freshDatabase()
		importPicked("subjects.hjson", { null }, text)

		assertEquals(listOf("Mathe", "Deutsch"), ItemRepository.subjects().map { it.name })
		assertStressTree(ItemRepository.subjects()[0], ItemRepository.subjects()[1])
		assertEquals(links, linkCount())
	}

	@Test
	fun `the root export imports into the open container through the workers`() {
		buildStressDb()
		val links = linkCount()
		val text = exportPicked(ExportFormat.JSON) { ItemRepository.subjects() }

		freshDatabase()
		val holder = ItemRepository.createSubject("Holder", null)
		val chapter = ItemRepository.createChapter(holder.id, "box", null)
		importPicked("subjects.hjson", { chapter }, text)

		// both subjects land as chapters under the open container, the
		// stress tree below keeps its shape and links
		assertEquals(listOf("box"), ItemRepository.children(holder.id).map { it.name })
		val box = ItemRepository.children(holder.id).single()
		val landed = ItemRepository.children(box.id)
		assertEquals(listOf("Mathe", "Deutsch"), landed.map { it.name })
		assertStressTree(landed[0], landed[1])
		// the box takes both root links, the holder chain adds its two
		assertEquals(links + 2, linkCount())
	}


	@Test
	fun `the selected chapters export imports into the open container through the workers`() {
		buildStressDb()
		val mathe = ItemRepository.subjects()[0]
		val children = ItemRepository.children(mathe.id)
		val c0 = children[0]
		val c1 = children[1]
		val text = exportPicked(ExportFormat.JSON) { listOf(c1, c0) }

		freshDatabase()
		val holder = ItemRepository.createSubject("Holder", null)
		val chapter = ItemRepository.createChapter(holder.id, "box", null)
		importPicked("selection.hjson", { chapter }, text)

		// both selected chapters land under the open container, in the own order
		// of the export, with the reference between them intact
		val box = ItemRepository.children(holder.id).single()
		assertEquals(listOf("c: one", "1. 100"), ItemRepository.children(box.id).map { it.name })
		val back = ItemRepository.children(box.id)
		assertTrue(ItemRepository.linkIsReference(back[0].id,
			ItemRepository.children(back[1].id)[0].id))
	}

	@Test
	fun `the import report matches the export report of the same content`() {
		val mathe = ItemRepository.createSubject("Mathe", null)
		val c0 = ItemRepository.createChapter(mathe.id, "c0", null)
		val c1 = ItemRepository.createChapter(mathe.id, "c1", null)
		val word = ItemRepository.createWord(c0.id, "Hund", null, listOf("pes" to null))
		// the word shows in a second chapter, so its file entry doubles while the
		// row does not
		ItemRepository.legacyLinkAtEnd(c1.id, word.id)
		ItemRepository.createWord(c0.id, "Schule", null, listOf("skola" to null))

		val (words, chapters) = ItemRepository.subgraphStats(listOf(mathe.id))
		val text = exportPicked(ExportFormat.JSON) { listOf(mathe) }

		freshDatabase()
		val result = ItemRepository.import(HJsonImporter(text))

		// the numbers the import worker reports, the same rows, not the doubled
		// entries of the file
		assertEquals(words, result.words)
		assertEquals(chapters, result.chapters)
	}

	@Test
	fun `the reimported database re-exports to the same file`() {
		buildStressDb()
		// a word shared into a second chapter, whose entry doubles in the file
		// while the row does not
		val mathe = ItemRepository.subjects()[0]
		val shared = ItemRepository.children(ItemRepository.children(mathe.id)[0].id)[0]
		val extra = ItemRepository.createChapter(mathe.id, "extra", null)
		ItemRepository.legacyLinkAtEnd(extra.id, shared.id)

		val text = exportPicked(ExportFormat.JSON) { ItemRepository.subjects() }

		freshDatabase()
		importPicked("subjects.hjson", { null }, text)

		// the identity of the data, not a hand-picked shape, so the round trip
		// reproduces the very same file
		assertEquals(text, exportPicked(ExportFormat.JSON) { ItemRepository.subjects() })
	}

	@Test
	fun `the word-list export of everything imports at the root through the workers`() {
		val mathe = ItemRepository.createSubject("Mathe", "subject desc")
		val c0 = ItemRepository.createChapter(mathe.id, "1. 100", "chapter desc")
		ItemRepository.createWord(c0.id, "Hund", "noun", listOf("dog" to null))
		ItemRepository.createNote(mathe.id, "a note", null)
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		ItemRepository.createWord(deutsch.id, "das Haus", null, listOf("house" to null))
		val text = exportPicked(ExportFormat.WORD) { ItemRepository.subjects() }

		freshDatabase()
		importPicked("subjects.txt", { null }, text)

		// both subjects come back through the word-list flow, while counters and
		// references are the documented losses of the format
		val subjects = ItemRepository.subjects()
		assertEquals(listOf("Mathe", "Deutsch"), subjects.map { it.name })
		val children = ItemRepository.children(subjects[0].id)
		assertEquals(listOf("1. 100", "a note"), children.map { it.name })
		val words = ItemRepository.children(children[0].id)
		assertEquals(listOf("Hund"), words.map { it.name })
		assertEquals("noun", words[0].description)
		assertEquals(listOf("dog"), ItemRepository.translations(words[0].id).map { it.name })
		assertEquals(0, words[0].passedTests)
		assertEquals(listOf("das Haus"), ItemRepository.children(subjects[1].id).map { it.name })
	}
}
