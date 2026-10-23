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
 * - the path covers children, links and the row build.
 * - a default-flipped word reads its translations per row.
 * - it runs through the production [MainExplorerViewModel] path to the list.
 * - the heaviest real subject NJ covers the subject and chapter open shapes.
 * - the subject open shows 204 chapter rows and no words.
 * - a synthetic 300-word chapter models the wide end.
 * - the counter read of the info strip runs after the list lands.
 * - that read is timed apart, since its budget is a report line.
 * - PERF open lines carry the numbers.
 * - the open budget is the edge of what a user notices, a quarter second.
 * - a per-row query storm thus fails the suite instead of only feeling slow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowseOpenPerformanceTest {

	private lateinit var db: ItemDatabase
	private val defFlipWas = ItemRow.defFlip

	/** Counting gate: the callback counts only while an open is measured. */
	private var counting = false

	/** The queries of the last counted open: a per-row storm is countable, not just slow. */
	private val queries = AtomicInteger()

	/** A statement the open owes its data to, while the bookkeeping of Room stays out. */
	private fun isDataStatement(sql: String): Boolean =
		!sql.startsWith("BEGIN") && !sql.startsWith("END") &&
			!sql.startsWith("TRANSACTION") && !sql.contains("room_table_modification_log")

	@Before
	fun setUp() {
		val app = RuntimeEnvironment.getApplication()
		GlobalDependencies.appContext = app
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			// the direct executor runs the callback on the querying thread:
			// - the count is exact once the inline open returns.
			// - transaction plumbing and the invalidation log of Room are not data.
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
		// the open path renders flipped words, according to the setting of the user
		ItemRow.defFlip = true
	}

	@After
	fun tearDown() {
		ItemRow.defFlip = defFlipWas
		db.close()
	}

	/** The edge of what a user notices on one navigation. */
	private val openBudgetMs = 250

	/** The strip read runs after the list, with tenfold headroom, so it blocks nothing. */
	private val stripBudgetMs = 1_000

	private fun <T> timed(name: String, budgetMs: Int, block: () -> T): T {
		val start = System.nanoTime()
		val result = block()
		val ms = (System.nanoTime() - start) / 1_000_000
		println("PERF $name: $ms ms")
		assertTrue("$name took $ms ms (budget $budgetMs ms)", ms < budgetMs)
		return result
	}

	private fun countQueries() {
		queries.set(0)
		counting = true
	}

	/**
	 * Stops counting and asserts the ceiling.
	 * - children and links.
	 * - one batch translations read.
	 * - the read and write of the info strip.
	 */
	private fun assertQueryBudget(label: String, budget: Int) {
		counting = false
		val count = queries.get()
		println("PERF $label-queries: $count")
		assertTrue("$label issued $count queries (budget $budget)", count <= budget)
	}

	// unconfined io on the main thread of Robolectric, so the open runs inline and
	// the measurement covers read to publish
	private fun model() = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)

	private fun njId(): Int = ItemRepository.subjects().single { it.name == "NJ" }.id

	/**
	 * The NJ descendant holding the most word rows, the strongest word-open shape.
	 * - NJ nests chapters inside chapters.
	 * - the search thus walks the whole subgraph for the busiest word holder.
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
		// children, links and the read and write of the strip, with no word rows, so
		// no translations read may appear
		assertQueryBudget("open-subject", 4)

		// the subject-wide name merge folds the 204 chapter entries of NJ into
		// distinct names, the children the open really shows
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
		// one batch translations read, whatever the word count, because a per-word
		// read storms this budget
		assertQueryBudget("open-wide-chapter", 5)

		assertEquals(300, model.items().size)
	}

	@Test
	fun `the info strip counter read stays under its budget`() {
		timed("info-counters", stripBudgetMs) { ItemRepository.recomputeCounters(njId()) }
	}
}
