package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import java.io.File
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
 * - a reference whose home lies outside the export writes its content in place.
 * - without that fallback the save would lose the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportImportMatrixTest {

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

	private fun freshDatabase() {
		db.close()
		val app = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	private fun buildRich(): DbItem {
		val subject = ItemRepository.createSubject("S", null)
		val c0 = ItemRepository.createChapter(subject.id, "c0", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val wRoot = ItemRepository.createWord(subject.id, "wRoot", null, listOf("t" to null))
		ItemRepository.createNote(subject.id, "noteRoot", "root note")
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null, "t2" to null))
		ItemRepository.createNote(c1.id, "note1", null)
		assertTrue(ItemRepository.reference(wRoot.id, c1.id))
		assertTrue(ItemRepository.reference(row("w1", ItemKind.WORD).id, c0.id))
		assertTrue(ItemRepository.reference(c1.id, c2.id))
		return subject
	}

	private fun row(name: String, kind: ItemKind): DbItem = ItemRepository.getByName(name, kind).single()

	/** The rich tree below a saved subject row, including shape, order and links. */
	private fun assertRich(subject: DbItem) {
		assertEquals("S", subject.name)
		val children = ItemRepository.children(subject.id)
		assertEquals(listOf("c0", "c1", "c2", "wRoot", "noteRoot"), children.map { it.name })
		val c0 = children[0]
		val c1 = children[1]
		val c2 = children[2]
		val wRoot = children[3]
		assertEquals(ItemKind.NOTE, children[4].type)
		assertEquals("root note", children[4].description)

		// the reference into c0 names a word whose home follows it in the file
		// order, so it resolves after every row is saved
		assertEquals(listOf("w1"), ItemRepository.children(c0.id).map { it.name })
		assertTrue(ItemRepository.linkIsReference(c0.id, ItemRepository.children(c0.id).single().id))

		// c1 holds its own word, its note and the referenced subject word
		val c1Children = ItemRepository.children(c1.id)
		assertEquals(listOf("w1", "note1", "wRoot"), c1Children.map { it.name })
		assertEquals(ItemKind.NOTE, c1Children[1].type)
		assertTrue(ItemRepository.linkIsReference(c1.id, wRoot.id))
		assertFalse(ItemRepository.linkIsReference(c1.id, c1Children[0].id))
		assertEquals(listOf("t1", "t2"), ItemRepository.translations(c1Children[0].id).map { it.name })

		assertTrue(ItemRepository.linkIsReference(c2.id, c1.id))
		assertEquals(listOf("c1"), ItemRepository.children(c2.id).map { it.name })
	}



	@Test
	fun `an anchored re-import merges and keeps every reference single`() {
		val subject = buildRich()
		val text = ItemRepository.export(HJsonExporter(), listOf(subject))

		freshDatabase()
		val holder = ItemRepository.createSubject("Holder", null)
		ItemRepository.import(HJsonImporter(text), holder.id)
		ItemRepository.import(HJsonImporter(text), holder.id)

		assertEquals(listOf("S"), ItemRepository.children(holder.id).map { it.name })
		// a doubled reference link would show its row twice
		assertRich(ItemRepository.children(holder.id).single())
	}

	@Test
	fun `a deep chapter export round trips at the root`() {
		val subject = buildRich()
		val text = ItemRepository.export(HJsonExporter(), listOf(row("c1", ItemKind.CHAPTER)))

		freshDatabase()
		ItemRepository.import(HJsonImporter(text))

		val back = ItemRepository.subjects().single()
		assertEquals("c1", back.name)
		val children = ItemRepository.children(back.id)
		// w1 and note1 land plain, and the referenced subject word is written in
		// place, because its home lies outside the exported chapter
		assertEquals(listOf("w1", "note1", "wRoot"), children.map { it.name })
		assertEquals(ItemKind.NOTE, children[1].type)
		assertFalse(ItemRepository.linkIsReference(back.id, children[2].id))
		assertEquals(listOf("t1", "t2"), ItemRepository.translations(children[0].id).map { it.name })
	}

	@Test
	fun `selected chapters export together and a reference to a selected root stays a link`() {
		val subject = buildRich()
		val text = ItemRepository.export(HJsonExporter(), listOf(row("c1", ItemKind.CHAPTER), row("c2", ItemKind.CHAPTER)))

		freshDatabase()
		ItemRepository.import(HJsonImporter(text))

		val subjects = ItemRepository.subjects()
		assertEquals(listOf("c1", "c2"), subjects.map { it.name })
		// the reference of c2 names the exported root c1 itself, the root path, with
		// no second copy of the chapter
		assertTrue(ItemRepository.linkIsReference(subjects[1].id, subjects[0].id))
		assertEquals(listOf("w1", "note1", "wRoot"), ItemRepository.children(subjects[0].id).map { it.name })
		assertEquals(listOf("c1"), ItemRepository.children(subjects[1].id).map { it.name })
	}


	@Test
	fun `a chapter export round trips as a word list`() {
		val subject = buildRich()
		val text = ItemRepository.export(WordListExporter(), listOf(row("c1", ItemKind.CHAPTER)))

		freshDatabase()
		ItemRepository.import(WordListImporter("c1", text))

		// the own wrapper line of the file IS the subject:
		// - its words and the note come back with the in-place copy.
		// - the reference link is the documented loss of this format.
		val back = ItemRepository.subjects().single()
		assertEquals("c1", back.name)
		assertEquals(listOf("w1", "note1", "wRoot"), ItemRepository.children(back.id).map { it.name })
		assertEquals(listOf("t1", "t2"), ItemRepository.translations(
			ItemRepository.children(back.id)[0].id).map { it.name })
	}

	/** The row count of everything but the invisible root. */
	private fun rowCount(): Int = db.openHelper.readableDatabase
		.query("SELECT COUNT(*) FROM item WHERE id != $ROOT_ID").use { cursor ->
			cursor.moveToFirst()
			cursor.getInt(0)
		}

	/** The link count, references included. */
	private fun linkCount(): Int = db.openHelper.readableDatabase
		.query("SELECT COUNT(*) FROM item_link").use { cursor ->
			cursor.moveToFirst()
			cursor.getInt(0)
		}

	@Test
	fun `the root export of the real NJ data round trips exactly`() {
		// the heaviest real subject with 204 chapters, shared words and a reference,
		// plus a second subject referencing into it
		val dir = File(javaClass.getResource("/perf/legacy/NJ")!!.toURI())
		val subject = LegacyJsonLoader.parseSubjectDir(
			LegacyFileSource { rel -> File(dir, rel).takeIf { it.isFile && it.length() > 0 }?.readText() },
			"NJ",
		) ?: throw IllegalStateException("missing legacy fixture")
		ItemRepository.import(LegacySubjectImporter(subject))
		val other = ItemRepository.createSubject("Other", null)
		val holder = ItemRepository.createChapter(other.id, "holder", null)
		ItemRepository.reference(
			ItemRepository.getByName("der Imbissstand", ItemKind.WORD).single().id, holder.id)

		val text = ItemRepository.export(HJsonExporter(), ItemRepository.subjects())
		val rows = rowCount()
		val links = linkCount()

		freshDatabase()
		ItemRepository.import(HJsonImporter(text))

		// the whole-database export is an array of subjects, so every one comes back
		// with every row, every link and the reference
		assertEquals(listOf("NJ", "Other"), ItemRepository.subjects().map { it.name })
		assertEquals(rows, rowCount())
		assertEquals(links, linkCount())
		val backHolder = ItemRepository.children(ItemRepository.subjects()[1].id).single()
		assertTrue(ItemRepository.linkIsReference(
			backHolder.id, ItemRepository.getByName("der Imbissstand", ItemKind.WORD).single().id))
		// the identity of the data: the heaviest real subject with its shared words
		// re-exports to the very same file
		assertEquals(text, ItemRepository.export(HJsonExporter(), ItemRepository.subjects()))
	}
}
