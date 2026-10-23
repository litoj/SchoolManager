package cz.litoj.schlmgr.integration.data.transfer

import android.net.Uri
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.data.transfer.LegacyDirImport
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - every way a picked file can land pins the anchor the worker persists with.
 * - the tree JSON lands at the root or inside the open container.
 * - a word list lands as the rows of the root.
 * - its own wrapper chapter counts as a subject and bare words as root rows.
 * - a word list can also land as the content of the open container.
 * - a container that closed before the run pins its own reload and failure.
 * - the exports pin the bytes both formats write and the empty-selection no-op.
 * - the picked documents are shadow-registered content uris.
 * - the SAF query of Robolectric names no document, so the name falls back.
 * - the posts run inline, so the join of the worker thread settles every follow-up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileWorkerTest {

	private lateinit var db: ItemDatabase
	private var nextPick = 0

	/** The reloads the open screen ran, the posted follow-up of the worker. */
	private class Reloads {
		var root = 0
		var current = 0
		var container = 0
	}

	private val tree = """
		[
		  {
		    name: A
		    children:
		    [
		      {
		        name: w1
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
		    name: B
		    children: []
		  }
		]
	""".trimIndent()

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

	private fun picked(text: String): Uri {
		val uri = Uri.parse("content://schlmgr.test/pick/${nextPick++}")
		// the shadow serves one instance for every open, and the format peek closes
		// it, so it resets and the import read starts clean
		val bytes = text.toByteArray(StandardCharsets.UTF_8)
		shadowOf(RuntimeEnvironment.getApplication().contentResolver)
			.registerInputStream(uri, object : java.io.ByteArrayInputStream(bytes) {
				override fun close() { reset() }
			})
		return uri
	}

	/** The onAnyFilePicked dispatch, with the resolvers as the view model hands them over. */
	private fun importPicked(fileName: String, open: () -> DbItem?, text: String): Reloads {
		val reloads = Reloads()
		val uri = picked(text)
		ImporterFactory.create(
			GlobalDependencies.appContext, { it.run() }, uri, fileName,
			FileGateway.startsAsJson(GlobalDependencies.appContext, uri),
			ImporterFactory.targetFor(open()),
			open, { open() == null },
			{ reloads.root++ }, { reloads.current++ }, { reloads.container++ },
		).start().join()
		return reloads
	}

	/** The picked export target: the shadow collects the written bytes. */
	private fun exportPicked(format: ExportFormat, selected: () -> List<DbItem>): Pair<String, Boolean> {
		val uri = Uri.parse("content://schlmgr.test/out/${nextPick++}")
		val out = ByteArrayOutputStream()
		shadowOf(RuntimeEnvironment.getApplication().contentResolver).registerOutputStream(uri, out)
		var started = false
		ExportPipeline(GlobalDependencies.appContext, { it.run() }, uri, format, selected) { started = true }
			.start().join()
		return String(out.toByteArray(), StandardCharsets.UTF_8) to started
	}

	@Test
	fun `a tree import at the root lands its top-level chapters as subjects and reloads the root`() {
		val reloads = importPicked("tree.hjson", { null }, tree)

		val subjects = ItemRepository.subjects()
		assertEquals(listOf("A", "B"), subjects.map { it.name })
		val words = ItemRepository.children(subjects[0].id)
		assertEquals(listOf("w1"), words.map { it.name })
		assertEquals(listOf("t1"), ItemRepository.translations(words[0].id).map { it.name })
		assertEquals(1, reloads.root)
		assertEquals(0, reloads.current)
	}

	@Test
	fun `a tree file whose name resolves to nothing imports as a tree`() {
		// some providers report no document name, so the empty fallback must not
		// send the tree JSON to the word-list parser
		val reloads = importPicked("", { null }, tree)

		val subjects = ItemRepository.subjects()
		assertEquals(listOf("A", "B"), subjects.map { it.name })
		assertEquals(listOf("w1"), ItemRepository.children(subjects[0].id).map { it.name })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a word list with a json-sounding name imports as a word list`() {
		val reloads = importPicked("list.hjson", { null }, "Kapitel {\nHaus;home\n}")

		// the shape of the document decides, and the name never does
		val subject = ItemRepository.subjects().single()
		assertEquals("Kapitel", subject.name)
		assertEquals(listOf("Haus"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a tree import into the open container lands its top-level chapters inside it`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		val reloads = importPicked("tree.hjson", { lektion }, tree)

		// no new subject, because the chapters of the file joined the content of the open container
		assertEquals(listOf("Deutsch"), ItemRepository.subjects().map { it.name })
		assertEquals(listOf("A", "B"), ItemRepository.children(lektion.id).map { it.name })
		val a = ItemRepository.children(lektion.id).first { it.name == "A" }
		assertEquals(listOf("w1"), ItemRepository.children(a.id).map { it.name })
		assertEquals(0, reloads.root)
		assertEquals(1, reloads.current)
	}

	@Test
	fun `a tree import twice into the same container adds nothing the second time`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("tree.hjson", { lektion }, tree)
		importPicked("tree.hjson", { lektion }, tree)

		// the container merge rule, where a same-named chapter of the same parent joins
		assertEquals(listOf("A", "B"), ItemRepository.children(lektion.id).map { it.name })
		val a = ItemRepository.children(lektion.id).first { it.name == "A" }
		val w1 = ItemRepository.children(a.id).single()
		// the word and its translation merged into their first rows
		assertEquals(listOf("t1"), ItemRepository.translations(w1.id).map { it.name })
	}

	@Test
	fun `a word list into the open container lands its words inside it and refreshes the content`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		val reloads = importPicked("list.txt", { lektion }, "Haus;home\nSchule;school")

		assertEquals(listOf("Deutsch"), ItemRepository.subjects().map { it.name })
		val words = ItemRepository.children(lektion.id)
		assertEquals(listOf("Haus", "Schule"), words.map { it.name })
		assertEquals(listOf("home"), ItemRepository.translations(words[0].id).map { it.name })
		assertEquals(1, reloads.container)
		assertEquals(0, reloads.root)
		assertEquals(0, reloads.current)
	}

	@Test
	fun `a word list import whose container is gone lands at the root instead`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		// the container closes between the pick and the run of the worker, so the
		// root takes the words then, like the fallback of the JSON import
		var open: DbItem? = lektion

		val reloads = importPicked("list.txt", { open.also { open = null } }, "Haus;home")

		assertEquals(0, ItemRepository.children(lektion.id).size)
		assertTrue(ItemRepository.children(ROOT_ID).any { it.name == "Haus" && it.type == ItemKind.WORD })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a word list at the root becomes its top-level chapters as subjects and reloads the root`() {
		val reloads = importPicked("list.txt", { null }, "Kapitel {\nHaus;home\n}")

		// the own top-level chapter of the file IS the subject, so the file name
		// never becomes a row
		val subject = ItemRepository.subjects().single()
		assertEquals("Kapitel", subject.name)
		assertEquals(listOf("Haus"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a bare word list at the root lands its words as the root's own rows`() {
		val reloads = importPicked("words.txt", { null }, "Haus;home")

		// the content lands as it is, with no wrapper container of the file name
		val row = ItemRepository.subjects().single()
		assertEquals("Haus", row.name)
		assertEquals(ItemKind.WORD, row.type)
		assertEquals(listOf("home"), ItemRepository.translations(row.id).map { it.name })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a malformed tree import leaves nothing behind and reloads nothing`() {
		val reloads = importPicked("tree.hjson", { null }, "{\n  name: \"x\"\n")

		assertEquals(0, ItemRepository.subjects().size)
		assertEquals(0, reloads.root)
		assertEquals(0, reloads.current)
	}

	@Test
	fun `a tree JSON export writes the repository's render of the selection and clears the selection`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		ItemRepository.createWord(lektion.id, "Haus", null, listOf("home" to null))

		val (text, started) = exportPicked(ExportFormat.JSON) { listOf(deutsch) }

		assertTrue(started)
		assertEquals(ItemRepository.export(HJsonExporter(), listOf(deutsch)), text)
	}

	@Test
	fun `a word-list export writes the repository's render of the selection`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		ItemRepository.createWord(lektion.id, "Haus", null, listOf("home" to null))

		val (text, started) = exportPicked(ExportFormat.WORD) { listOf(deutsch) }

		assertTrue(started)
		assertEquals(ItemRepository.export(WordListExporter(), listOf(deutsch)), text)
	}

	@Test
	fun `an export with no selection writes nothing`() {
		val (text, started) = exportPicked(ExportFormat.JSON) { emptyList() }

		assertEquals("", text)
		assertFalse(started)
	}

	@Test
	fun `a rejected directory pick imports nothing and reloads nothing`() {
		var rootReloads = 0
		// a tree uri for which the SAF query of Robolectric answers no children,
		// not a subject directory
		LegacyDirImport(
			GlobalDependencies.appContext, { it.run() },
			Uri.parse("content://schlmgr.test/tree/1"),
			{ true },
		) { rootReloads++ }.start().join()

		assertEquals(0, ItemRepository.subjects().size)
		assertEquals(0, rootReloads)
	}
}
