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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - NJ has 204 chapters, 3282 words and 3327 translations.
 * - the stack holds pure codecs and the real in-memory Room database.
	 * - only the row counts are asserted, the PERF lines print the wall-clock numbers.
 * - a per-item query storm fails the suite instead of only feeling slow.
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
		ItemRepository.resetForTest()
	}

	/**
	 * The saved-row counts of NJ.
	 * - the subject-wide name merge folds the repeated word entries.
	 * - 3282 entries become 3023 rows.
	 * - the names trim before the merge, so every format lands the same rows.
	 */
	private val njWords = 3023
	private val njChapters = 204
	private val njRows = 6251

	private fun fixture(path: String): File =
		File(javaClass.getResource(path)!!.toURI())

	private fun legacyImporter(): LegacySubjectImporter {
		val dir = fixture("/perf/legacy/NJ")
		val subject = LegacyJsonLoader.parseSubjectDir(
			LegacyFileSource { rel -> File(dir, rel).takeIf { it.isFile && it.length() > 0 }?.readText() },
			"NJ",
		) ?: throw IllegalStateException("missing legacy fixture")
		return LegacySubjectImporter(subject)
	}

	/** Prints the wall-clock number, since only the row counts are asserted. */
	private fun <T> measured(name: String, block: () -> T): T {
		val start = System.nanoTime()
		val result = block()
		println("PERF $name: ${(System.nanoTime() - start) / 1_000_000} ms")
		return result
	}

	private fun rowCount(): Int = db.openHelper.readableDatabase
		.query("SELECT COUNT(*) FROM item WHERE id != $ROOT_ID").use { cursor ->
			cursor.moveToFirst()
			cursor.getInt(0)
		}

	@Test
	fun `the directory import of NJ lands its rows`() {
		val importer = legacyImporter()
		val result = measured("legacy-dir") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
	}

	@Test
	fun `the tree JSON import of NJ lands its rows`() {
		val importer = HJsonImporter(fixture("/perf/nj.hjson").readText())
		val result = measured("json-tree") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
	}

	@Test
	fun `the word list imported as a subject lands its rows`() {
		val importer = WordListImporter("NJ", fixture("/perf/nj.txt").readText())
		val result = measured("word-as-subject") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
		assertEquals("NJ", ItemRepository.subjects().single().name)
	}

	@Test
	fun `the word list imported into a container lands its rows`() {
		val deck = ItemRepository.createSubject("Deck", null)
		val importer = WordListImporter("Deck", fixture("/perf/nj.txt").readText())
		val result = measured("word-into-container") {
			ItemRepository.import(importer, deck.id)
		}
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		// the deck row plus the trimmed row count of the word list
		assertEquals(njRows + 1, rowCount())
		// the NJ wrapper of the stream becomes a chapter of the deck
		val children = ItemRepository.children(deck.id)
		assertEquals(listOf("NJ"), children.map { it.name })
	}

	@Test
	fun `re-importing NJ merges and adds nothing`() {
		ItemRepository.import(legacyImporter())
		val importer = legacyImporter()
		// the merge path, where every name lookup hits an existing row and nothing doubles
		val result = measured("legacy-dir-reimport") { ItemRepository.import(importer) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows, rowCount())
		assertEquals(1, ItemRepository.subjects().size)
	}

	@Test
	fun `re-importing into a container merges and adds nothing`() {
		val deck = ItemRepository.createSubject("Deck", null)
		ItemRepository.import(WordListImporter("Deck", fixture("/perf/nj.txt").readText()), deck.id)
		// the subject being added to already holds the whole stream, so every lookup
		// hits a row of the preloaded scope and nothing doubles
		val importer = WordListImporter("Deck", fixture("/perf/nj.txt").readText())
		val result = measured("word-into-container-reimport") { ItemRepository.import(importer, deck.id) }
		assertEquals(njWords, result.words)
		assertEquals(njChapters, result.chapters)
		assertEquals(njRows + 1, rowCount())
		assertEquals(listOf("NJ"), ItemRepository.children(deck.id).map { it.name })
	}
}
