package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
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
 * - the analysis runs before the flip, so its cost is measured alone.
 * - the heavy NJ subject plus 300 synthetic words stresses a wide chapter.
 * - one seeded scope keeps the read count flat as the chapter grows.
 * - a lookup per word would storm the query budget.
 * - the per-word writes are cheap, so only the analysis carries a budget.
 * - the PERF lines print the numbers at the edge of user tolerance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlipPerformanceTest {

	private lateinit var db: ItemDatabase

	/** Keeps unmeasured queries out of the count. */
	private var counting = false

	/** The queries of the last counted run. A per-row storm is countable, not just slow. */
	private val queries = AtomicInteger()

	/** A data statement of the flip. The Room bookkeeping stays out. */
	private fun isDataStatement(sql: String): Boolean =
		!sql.startsWith("BEGIN") && !sql.startsWith("END") &&
			!sql.startsWith("TRANSACTION") && !sql.contains("room_table_modification_log")

	@Before
	fun setUp() {
		val app = RuntimeEnvironment.getApplication()
		GlobalDependencies.appContext = app
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			// the direct executor runs the callback inline, so the count is exact on return
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
	}

	@After
	fun tearDown() {
		db.close()
	}

	/** The edge of what a user tolerates on one bulk action. */
	private val flipBudgetMs = 1_500

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

	/** Closes the gate before the count is compared to the ceiling. */
	private fun assertQueryBudget(label: String, budget: Int) {
		counting = false
		val count = queries.get()
		println("PERF $label-queries: $count")
		assertTrue("$label issued $count queries (budget $budget)", count <= budget)
	}

	private fun njId(): Int = ItemRepository.subjects().single { it.name == "NJ" }.id

	/** A wide chapter stresses the analysis at the counted read scope. */
	private fun wideChapter(): Int {
		val deck = ItemRepository.createChapter(njId(), "wide", null)
		repeat(300) { i ->
			ItemRepository.createWord(deck.id, "wort $i", null, listOf("trl $i" to null))
		}
		return deck.id
	}

	@Test
	fun `the conflict analysis of a wide chapter stays under its budget`() {
		val deck = wideChapter()
		val nj = njId()

		countQueries()
		timed("flip-analysis", flipBudgetMs) { ItemRepository.startFlip(listOf(nj), listOf(deck)) }
		// one scope serves every word, so a read per word would storm the budget
		assertQueryBudget("flip-analysis", 2)
	}

	@Test
	fun `flipping a wide chapter stays under its budget`() {
		val deck = wideChapter()

		timed("flip-wide", flipBudgetMs) { ItemRepository.flip(listOf(deck)) }

		// one translation per word proves the flip covered the whole chapter
		assertEquals(300, ItemRepository.children(deck).count { it.name.startsWith("trl") })
	}
}
