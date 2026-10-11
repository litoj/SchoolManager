package cz.litoj.schlmgr.integration.ui.screen.subjects

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
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
 * The performance floor of one browse open — the read behind tapping a row:
 * children + links + row build (a default-flipped word reads its translations
 * per row), through the production [MainExplorerViewModel] path, to the
 * published list.
 *
 * - the heaviest real subject (NJ) covers both open shapes: the subject
 *   itself (204 chapter rows, no words) and a chapter of word rows.
 * - a synthetic 300-word chapter models the wide end.
 * - the info strip's counter read runs after the list lands — timed apart,
 *   its budget is a report line's, not the open's.
 * - `PERF open-*` lines carry the numbers; the open budget is the edge of
 *   what a user notices on a navigation (a quarter second), so a per-row
 *   query storm fails the suite instead of only feeling slow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowseOpenPerformanceTest {

	private lateinit var db: ItemDatabase
	private val defFlipWas = ItemRow.defFlip

	/** Counting gate — the callback counts only while an open is measured. */
	private var counting = false

	/** The queries of the last counted open — a per-row storm is countable, not just slow. */
	private val queries = AtomicInteger()

	/** A statement the open owes its data to — Room's own bookkeeping stays out. */
	private fun isDataStatement(sql: String): Boolean =
		!sql.startsWith("BEGIN") && !sql.startsWith("END") &&
			!sql.startsWith("TRANSACTION") && !sql.contains("room_table_modification_log")

	@Before
	fun setUp() {
		val app = RuntimeEnvironment.getApplication()
		GlobalDependencies.appContext = app
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			// the direct executor runs the callback on the querying thread, so
			// the count is exact once the inline open returns; transaction
			// plumbing and Room's invalidation log are not data statements
			.setQueryCallback({ sql, _ -> if (counting && isDataStatement(sql)) queries.incrementAndGet() },
				Executor(Runnable::run))
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		val dir = File(javaClass.getResource("/perf/legacy/NJ")!!.toURI())
		val subject = LegacyJsonLoader.parseSubjectDir(
			LegacyFileSource { rel -> File(dir, rel).takeIf { it.isFile && it.length() > 0 }?.readText() },
			"NJ",
		) ?: throw IllegalStateException("missing legacy NJ fixture")
		ItemRepository.import(LegacySubjectImporter(subject))
		// the open path renders flipped words — the user's setting
		ItemRow.defFlip = true
	}

	@After
	fun tearDown() {
		ItemRow.defFlip = defFlipWas
		db.close()
	}

	/** The edge of what a user notices on one navigation. */
	private val openBudgetMs = 250

	/** The strip read runs after the list — tenfold headroom, it blocks nothing. */
	private val stripBudgetMs = 1_000

	private fun <T> timed(name: String, budgetMs: Int, block: () -> T): T {
		val start = System.nanoTime()
		val result = block()
		val ms = (System.nanoTime() - start) / 1_000_000
		println("PERF $name: $ms ms")
		assertTrue("$name took $ms ms (budget $budgetMs ms)", ms < budgetMs)
		return result
	}

	/** Starts counting the open's queries. */
	private fun countQueries() {
		queries.set(0)
		counting = true
	}

	/**
	 * Stops counting and asserts the ceiling — children, links, one batch
	 * translations read, and the info strip's read and write.
	 */
	private fun assertQueryBudget(label: String, budget: Int) {
		counting = false
		val count = queries.get()
		println("PERF $label-queries: $count")
		assertTrue("$label issued $count queries (budget $budget)", count <= budget)
	}

	// Unconfined io on Robolectric's main thread — the open runs inline, so
	// the measurement covers read to publish.
	private fun model() = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)

	private fun njId(): Int = ItemRepository.subjects().single { it.name == "NJ" }.id

	/**
	 * The NJ descendant holding the most word rows — the strongest word-open
	 * shape. NJ nests chapters inside chapters, so the search walks the
	 * whole subgraph for the busiest word holder.
	 */
	private fun busiestChapterId(): Int {
		val nj = njId()
		db.openHelper.readableDatabase
			.query(
				"WITH RECURSIVE des(id) AS (SELECT $nj " +
					"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
					"SELECT l.parent_id FROM item_link l " +
					"INNER JOIN item i ON i.id = l.child_id AND i.type = 'word' " +
					"WHERE l.parent_id IN (SELECT id FROM des) " +
					"GROUP BY l.parent_id ORDER BY COUNT(*) DESC LIMIT 1",
			).use { cursor ->
				assertTrue("no chapter under NJ", cursor.moveToFirst())
				return cursor.getInt(0)
			}
	}

	@Test
	fun `opening the heaviest subject's chapter list stays under its budget`() {
		val model = model()
		val nj = model.items().single { it.item.name == "NJ" }

		countQueries()
		timed("open-subject", openBudgetMs) { model.onItemClick(nj) }
		// children, links and the strip's read and write — no word rows, so
		// no translations read may appear
		assertQueryBudget("open-subject", 4)

		// the subject-wide name merge folds NJ's 204 chapter entries into
		// distinct names — the children the open really shows
		assertEquals(34, model.items().size)
	}

	@Test
	fun `opening the busiest chapter stays under its budget`() {
		val model = model()
		val busiest = ItemRepository.get(busiestChapterId())!!

		timed("open-chapter", openBudgetMs) { model.loadContent(busiest) }

		assertTrue("the chapter loaded no rows", model.items().isNotEmpty())
	}

	@Test
	fun `opening a wide 300-word chapter stays under its budget`() {
		val deck = ItemRepository.createChapter(njId(), "wide", null)
		repeat(300) { i ->
			ItemRepository.createWord(deck.id, "wort $i", null, listOf("trl $i" to null))
		}
		val model = model()
		model.onItemClick(model.items().single { it.item.name == "NJ" })
		val chapter = model.items().single { it.item.name == "wide" }

		countQueries()
		timed("open-wide-chapter", openBudgetMs) { model.onItemClick(chapter) }
		// one batch translations read, whatever the word count — a per-word
		// read storms this budget
		assertQueryBudget("open-wide-chapter", 5)

		assertEquals(300, model.items().size)
	}

	@Test
	fun `the info strip counter read stays under its budget`() {
		timed("info-counters", stripBudgetMs) { ItemRepository.recomputeCounters(njId()) }
	}
}
