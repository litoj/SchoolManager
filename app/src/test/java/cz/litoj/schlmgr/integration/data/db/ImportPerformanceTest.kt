package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import java.io.File
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
 * The performance floor of every import kind, on the heaviest real subject
 * (NJ, 204 chapters, 3282 words, 3327 translations — 6813 stream items).
 *
 * - the whole import stack sits behind [ItemRepository.import] — pure codecs,
 *   the real in-memory Room database, nothing else.
 * - each test times its import (`PERF <name>`) against a ceiling — a per-item
 *   query storm fails the suite instead of only feeling slow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportPerformanceTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
	}

	/**
	 * The NJ saved-row counts — the subject-wide name merge folds the
	 * repeated word entries (3282 entries, 3023 rows: the names trim
	 * before the merge, so every format lands the same rows).
	 */
	private val njWords = 3023
	private val njChapters = 204
	private val njRows = 6251

	/** The fixture file at [path] — a committed test resource. */
	private fun fixture(path: String): File =
		File(javaClass.getResource(path)!!.toURI())

	/** The NJ directory as the pipeline hands it to the repository. */
	private fun legacyImporter(): LegacySubjectImporter {
		val dir = fixture("/perf/legacy/NJ")
		val subject = LegacyJsonLoader.parseSubjectDir(
			LegacyFileSource { rel -> File(dir, rel).takeIf { it.isFile && it.length() > 0 }?.readText() },
			"NJ",
		) ?: throw IllegalStateException("missing legacy fixture")
		return LegacySubjectImporter(subject)
	}

	/**
	 * The wall-clock budget of one NJ import through the repository. The
	 * fixed save loop runs in well under a second, with tenfold headroom for
	 * machine variance — per-item database reads fail loudly.
	 */
	private val budgetMs = 5_000

	private fun <T> timed(name: String, block: () -> T): T {
		val start = System.nanoTime()
		val result = block()
		val ms = (System.nanoTime() - start) / 1_000_000
		println("PERF $name: $ms ms")
		assertTrue("$name took $ms ms (budget $budgetMs ms)", ms < budgetMs)
		return result
	}

	/** The one import's row count, everything the root reaches. */
	private fun rowCount(): Int = db.openHelper.readableDatabase
		.query("SELECT COUNT(*) FROM item WHERE id != $ROOT_ID").use { cursor ->
			cursor.moveToFirst()
			cursor.getInt(0)
		}

	@Test
	fun `the directory import of NJ stays under its time budget`() {
		val importer = legacyImporter()
		val result = timed("legacy-dir") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
	}

	@Test
	fun `the tree JSON import of NJ stays under its time budget`() {
		val importer = HJsonImporter(fixture("/perf/nj.hjson").readText())
		val result = timed("json-tree") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
	}

	@Test
	fun `the word list imported as a subject stays under its time budget`() {
		val importer = WordListImporter("NJ", fixture("/perf/nj.txt").readText())
		val result = timed("word-as-subject") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
		assertEquals("NJ", ItemRepository.subjects().single().name)
	}

	@Test
	fun `the word list imported into a container stays under its time budget`() {
		val deck = ItemRepository.createSubject("Deck", null)
		val importer = WordListImporter("Deck", fixture("/perf/nj.txt").readText())
		val result = timed("word-into-container") {
			ItemRepository.import(importer, deck.id)
		}
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		// the deck row plus the word list's trimmed row count
		assertEquals(njRows + 1, rowCount())
		// the stream's NJ wrapper becomes a chapter of the deck
		val children = ItemRepository.children(deck.id)
		assertEquals(listOf("NJ"), children.map { it.name })
	}

	@Test
	fun `re-importing NJ merges under the time budget and adds nothing`() {
		ItemRepository.import(legacyImporter())
		val importer = legacyImporter()
		// the merge path — every name lookup hits an existing row, nothing doubles
		val result = timed("legacy-dir-reimport") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
		assertEquals(1, ItemRepository.subjects().size)
	}

	@Test
	fun `re-importing into a container merges under the time budget and adds nothing`() {
		val deck = ItemRepository.createSubject("Deck", null)
		ItemRepository.import(WordListImporter("Deck", fixture("/perf/nj.txt").readText()), deck.id)
		// the subject being added to already holds the whole stream — every
		// lookup hits a row of the preloaded scope, nothing doubles
		val importer = WordListImporter("Deck", fixture("/perf/nj.txt").readText())
		val result = timed("word-into-container-reimport") { ItemRepository.import(importer, deck.id) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows + 1, rowCount())
		assertEquals(listOf("NJ"), ItemRepository.children(deck.id).map { it.name })
	}
}
