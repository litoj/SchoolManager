package cz.litoj.schlmgr.integration.data.transfer

import android.net.Uri
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportDestination
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.popup.WordListFixPopup
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * - its own wrapper chapter counts as a subject.
 * - a bare word list lands in a container named after the file.
 * - a word list can also land as the content of the open container.
 * - a container that closed before the run pins its own reload and failure.
 * - the exports pin the bytes both formats write and the empty-selection no-op.
 * - the picked documents are shadow-registered content uris.
 * - the grant of a picked file leaves with a stored import and stays through a failure.
 * - the SAF query of Robolectric names no document, so the name falls back.
 * - the posts run inline, so the join of the worker thread settles every follow-up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileWorkerTest {

	private lateinit var db: ItemDatabase
	private var nextPick = 0

	/** The provider door caches one folder per JVM, so the share tests watch the file. */
	private val realDoor = FileGateway.shareUri

	/** The document of the last [importPicked], the file a failure offers for Edit. */
	private var lastPick: Uri? = null

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
		FileGateway.shareUri = realDoor
		db.close()
		ItemRepository.resetForTest()
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

	/**
	 * The picked-file dispatch, with the resolvers as the view model hands them over.
	 * - [holds] is the grant the pick of the screen takes, [retry] the door of its editor.
	 */
	private fun importPicked(
		fileName: String, open: () -> DbItem?, text: String,
		holds: Boolean = false, retry: ((Uri) -> Unit)? = null,
	): Reloads {
		val reloads = Reloads()
		val uri = picked(text)
		lastPick = uri
		if (holds) hold(uri)
		val worker = ImporterFactory.createPickedImport(
			GlobalDependencies.appContext, { it.run() }, uri, fileName,
			open, { open() == null },
			{ reloads.root++ }, { reloads.current++ }, { reloads.container++ },
		)
		worker.onReimport = retry
		worker.start().join()
		return reloads
	}

	/** The picked export target: the shadow collects the written bytes. */
	private fun exportPicked(format: ExportFormat, selected: () -> List<DbItem>): Pair<String, Boolean> {
		val uri = Uri.parse("content://schlmgr.test/out/${nextPick++}")
		val out = ByteArrayOutputStream()
		shadowOf(RuntimeEnvironment.getApplication().contentResolver).registerOutputStream(uri, out)
		var started = false
		ExportPipeline(GlobalDependencies.appContext, { it.run() }, ExportDestination.Document(uri),
			format, selected) { started = true }
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
	fun `the shape of the document picks the parser, its name never does`() {
		// some providers report no document name, and others report a json one for
		// a word list, so the text alone decides
		val wordList = importPicked("list.hjson", { null }, "Kapitel {\nHaus;home\n}")

		val subject = ItemRepository.subjects().single()
		assertEquals("Kapitel", subject.name)
		assertEquals(listOf("Haus"), ItemRepository.children(subject.id).map { it.name })
		assertEquals(1, wordList.root)

		val noName = importPicked("", { null }, tree)

		assertEquals(listOf("Kapitel", "A", "B"), ItemRepository.subjects().map { it.name })
		assertEquals(listOf("w1"), ItemRepository.children(ItemRepository.subjects()[1].id).map { it.name })
		assertEquals(1, noName.root)
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
	fun `a bare word list into the open container lands in a chapter named after the file`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		val reloads = importPicked("list.txt", { lektion }, "Haus;home\nSchule;school")

		assertEquals(listOf("Deutsch"), ItemRepository.subjects().map { it.name })
		val list = ItemRepository.children(lektion.id).single()
		assertEquals("list", list.name)
		assertEquals(ItemKind.CHAPTER, list.type)
		val words = ItemRepository.children(list.id)
		assertEquals(listOf("Haus", "Schule"), words.map { it.name })
		assertEquals(listOf("home"), ItemRepository.translations(words[0].id).map { it.name })
		assertEquals(1, reloads.container)
		assertEquals(0, reloads.root)
		assertEquals(0, reloads.current)
	}

	@Test
	fun `a bare word list keeps the dots of a name without a known suffix`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("U1.1", { lektion }, "Haus;home")

		assertEquals(listOf("U1.1"), ItemRepository.children(lektion.id).map { it.name })
	}

	@Test
	fun `a top level of notes alone lands in a chapter named after the file`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		// any entry that is not a chapter takes the wrapper, a note included
		importPicked("list.txt", { lektion }, "= text [a note]")

		val list = ItemRepository.children(lektion.id).single()
		assertEquals("list", list.name)
		assertEquals(listOf("text"), ItemRepository.children(list.id).map { it.name })
	}

	@Test
	fun `a bare word list into a container named like the file lands its words inside it`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("Lektion 1.txt", { lektion }, "Haus;home\n= text [a note]")

		// the open container already is the chapter of the file
		assertEquals(listOf("Haus", "text"), ItemRepository.children(lektion.id).map { it.name })
	}

	@Test
	fun `a tree file wrapped in the name of the open subject fills that subject`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val wrapped = "{\nname: Deutsch\nchildren: [\n{\nname: Německo 20\nchildren: [\n" +
			"{ \"name\": \"gesund\", \"translations\": [{ \"name\": \"zdravý\" }] }\n]\n}\n]\n}"

		importPicked("Deutsch.hjson", { deutsch }, wrapped)

		// the subject made before the import already is the container of the file
		assertEquals(listOf(deutsch.id), ItemRepository.subjects().map { it.id })
		val chapter = ItemRepository.children(deutsch.id).single()
		assertEquals("Německo 20", chapter.name)
		assertEquals(listOf("gesund"), ItemRepository.children(chapter.id).map { it.name })
	}

	@Test
	fun `a wrapped import fills a target that only references hold`() = runBlocking {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		val outer = ItemRepository.createChapter(deutsch.id, "outer", null)
		ItemRepository.reference(lektion.id, outer.id)
		db.itemDao().deleteLink(deutsch.id, lektion.id)

		importPicked("anything.txt", { lektion }, "Lektion 1 {\nHaus;home\n}")

		// the reference is the same row, so the file fills it like any plain child
		assertEquals(listOf(lektion.id), ItemRepository.children(outer.id).map { it.id })
		assertEquals(listOf("Haus"), ItemRepository.children(lektion.id).map { it.name })
	}

	@Test
	fun `a word list wrapped in the name of the open chapter fills that chapter`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("anything.txt", { lektion }, "Lektion 1 {\nHaus;home\n}")

		assertEquals(listOf(lektion.id), ItemRepository.children(deutsch.id).map { it.id })
		assertEquals(listOf("Haus"), ItemRepository.children(lektion.id).map { it.name })
	}

	@Test
	fun `a bare word list joins the chapter of the file name the container already holds`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		val list = ItemRepository.createChapter(lektion.id, "list", null)
		ItemRepository.createWord(list.id, "Hund", null, listOf("dog" to null))

		importPicked("list.txt", { lektion }, "Haus;home")

		assertEquals(listOf(list.id), ItemRepository.children(lektion.id).map { it.id })
		assertEquals(listOf("Hund", "Haus"), ItemRepository.children(list.id).map { it.name })
	}

	@Test
	fun `a word list of chapters only into the open container gets no chapter of the file name`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("list.txt", { lektion }, "A {\nHaus;home\n}\nB {\nSchule;school\n}")

		assertEquals(listOf("A", "B"), ItemRepository.children(lektion.id).map { it.name })
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
		val list = ItemRepository.subjects().single { it.name == "list" }
		assertEquals(listOf("Haus"), ItemRepository.children(list.id).map { it.name })
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
	fun `a bare word list at the root lands in a subject named after the file`() {
		val reloads = importPicked("words.txt", { null }, "Haus;home\nKapitel {\nSchule;school\n}")

		val subject = ItemRepository.subjects().single()
		assertEquals("words", subject.name)
		assertEquals(ItemKind.CHAPTER, subject.type)
		val rows = ItemRepository.children(subject.id)
		assertEquals(listOf("Haus", "Kapitel"), rows.map { it.name })
		assertEquals(listOf("home"), ItemRepository.translations(rows[0].id).map { it.name })
		assertEquals(1, reloads.root)
	}

	@Test
	fun `a bare word list at the root joins the subject of the file name`() {
		val words = ItemRepository.createSubject("words", null)
		ItemRepository.createWord(words.id, "Hund", null, listOf("dog" to null))

		importPicked("words.txt", { null }, "Haus;home")

		assertEquals(listOf(words.id), ItemRepository.subjects().map { it.id })
		assertEquals(listOf("Hund", "Haus"), ItemRepository.children(words.id).map { it.name })
	}

	@Test
	fun `a bare word list without a file name lands its words as the root's own rows`() {
		importPicked("", { null }, "Haus;home")

		val row = ItemRepository.subjects().single()
		assertEquals("Haus", row.name)
		assertEquals(ItemKind.WORD, row.type)
		assertEquals(listOf("home"), ItemRepository.translations(row.id).map { it.name })
	}

	@Test
	fun `a bare word list without a file name into the open container lands its words inside it`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)

		importPicked("", { lektion }, "Haus;home")

		assertEquals(listOf("Haus"), ItemRepository.children(lektion.id).map { it.name })
	}

	@Test
	fun `a finished import reports its top-level rows to the caller`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion", null)
		var top: List<DbItem>? = null
		val uri = picked("Kapitel {\nHaus;home\n}")
		val worker = ImporterFactory.createPickedImport(
			GlobalDependencies.appContext, { it.run() }, uri, "list.txt",
			{ lektion }, { false }, {}, {}, {},
		)
		worker.onImported = { result -> top = result.topLevel }

		worker.start().join()

		assertEquals(listOf("Kapitel"), top?.map { it.name })
	}

	@Test
	fun `a word list parse failure opens a persistent popup`() {
		val deutsch = ItemRepository.createSubject("Deutsch", null)
		val lektion = ItemRepository.createChapter(deutsch.id, "Lektion 1", null)
		Dialogs.clear()

		importPicked("Broken.txt", { lektion }, "Haus\n")
		assertTrue(Dialogs.isShowing<MessagePopup>())
		Dialogs.clear()
	}

	@Test
	fun `a one-character word list mistake mends itself and reports its line`() = runBlocking {
		Dialogs.clear()
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeout(2_000) { GlobalDependencies.messages.first() }
		}

		importPicked("list.txt", { null }, "Kapitel {\nHaus;home;extra\n}\n")

		val word = ItemRepository.children(ItemRepository.subjects().single().id).single()
		assertEquals(listOf("home", "extra"), ItemRepository.translations(word.id).map { it.name })

		val report = message.await() as GlobalDependencies.UiMessage.Text
		val fixed = RuntimeEnvironment.getApplication().resources
			.getQuantityString(R.plurals.import_fixed_lines, 1, 1)
		assertTrue("The report hides the repair: ${report.text}", report.text.contains(fixed))
		assertTrue("The copy hides the repaired line: ${report.fullText}",
			report.fullText.contains("Haus;home;extra → Haus;home\\extra"))
		assertFalse(Dialogs.isShowing<MessagePopup>())
	}

	/** The picked file held for the editor, as the pick of the screen would hold it. */
	private fun hold(uri: Uri) = FileGateway.holdForEdit(GlobalDependencies.appContext, uri)

	private fun held(uri: Uri?) =
		GlobalDependencies.appContext.contentResolver.persistedUriPermissions
			.filter { it.uri == uri }

	@Test
	fun `an unrepairable line imports nothing and reports its file`() {
		Dialogs.clear()

		importPicked("Broken.txt", { null }, "Kapitel {\nHaus->home\n}\n")

		assertTrue("no editor without an owner to import the file again",
			Dialogs.stack.value.single() is MessagePopup)
		assertEquals(emptyList<String>(), ItemRepository.subjects().map { it.name })
		Dialogs.clear()
	}

	@Test
	fun `a failure whose import can run again opens the editor instead`() {
		Dialogs.clear()
		val again = mutableListOf<Uri>()

		importPicked("Broken.txt", { null }, "Kapitel {\nHaus->home\n}\n", retry = { again.add(it) })

		assertTrue("the editor is the only dialog of a fixable file",
			Dialogs.stack.value.single() is WordListFixPopup)
		assertEquals(emptyList<String>(), ItemRepository.subjects().map { it.name })
		Dialogs.clear()
	}

	@Test
	fun `a stored import gives the grant of its file back`() {
		val lektion = ItemRepository.createChapter(
			ItemRepository.createSubject("Deutsch", null).id, "Lektion 1", null)

		importPicked("list.txt", { lektion }, "Haus;home", holds = true)

		assertTrue("the imported file holds no grant", held(lastPick).isEmpty())
	}

	@Test
	fun `a failed import keeps the grant of its file for the editor`() {
		Dialogs.clear()

		importPicked("Broken.txt", { null }, "Kapitel {\nHaus->home\n}\n", holds = true)

		assertTrue("the editor still reaches the file", held(lastPick).isNotEmpty())
		Dialogs.clear()
	}

	@Test
	fun `a malformed tree import reports once and leaves nothing behind`() = runBlocking {
		Dialogs.clear()
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeout(2_000) { GlobalDependencies.messages.first() }
		}

		val reloads = importPicked("tree.hjson", { null }, "{\n  name: \"x\"\n")

		// the snackbar of the load failure: no popup, no half tree, no reload
		assertTrue((message.await() as GlobalDependencies.UiMessage.Text).text.isNotEmpty())
		assertFalse(Dialogs.isShowing<MessagePopup>())
		assertEquals(0, ItemRepository.subjects().size)
		assertEquals(0, reloads.root)
		assertEquals(0, reloads.current)
	}

	@Test
	fun `an export of no selection writes nothing and opens no sheet`() = runBlocking {
		val (text, started) = exportPicked(ExportFormat.JSON) { emptyList() }

		assertEquals("", text)
		assertFalse(started)

		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeoutOrNull(300) { GlobalDependencies.messages.first() }
		}

		ExportPipeline(
			GlobalDependencies.appContext, { it.run() },
			ExportDestination.Share("Kapitel.txt"), ExportFormat.WORD,
			{ emptyList() }, {},
		).start().join()

		assertNull("an empty selection shares nothing", message.await())
	}

	@Test
	fun `a shared export hands its own copy to the share sheet`() = runBlocking {
		FileGateway.shareUri = { _, file -> Uri.fromFile(file) }
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeout(2_000) { GlobalDependencies.messages.first() }
		}
		val subject = ItemRepository.createSubject("Kapitel", null)
		ItemRepository.createWord(subject.id, "w1", null, listOf("t1" to null))
		var started = false

		ExportPipeline(
			GlobalDependencies.appContext, { it.run() },
			ExportDestination.Share("Kapitel.txt"), ExportFormat.WORD,
			{ ItemRepository.subjects() }, { started = true },
		).start().join()

		val sent = message.await() as GlobalDependencies.UiMessage.SendFile
		assertEquals("Kapitel.txt", sent.uri.lastPathSegment)
		assertEquals("text/plain", sent.mime)
		assertTrue(started)

		// the copy is what the same selection writes into a picked document
		val copied = File(sent.uri.path!!).readText()
		val written = exportPicked(ExportFormat.WORD) { ItemRepository.subjects() }.first
		assertEquals("the sheet receives another text", written, copied)
	}
}
