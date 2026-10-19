package cz.litoj.schlmgr.e2e.ui.component

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import cz.litoj.schlmgr.ui.component.ItemList
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchIndex
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.toUi
import java.io.File
import java.time.Duration
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The scroll floor of the explorer list under the fastest real scroll: one maximum-velocity fling pass
 * through a whole list, one hand-stepped frame at a time, against the same count of one-line rows.
 * - a frame's wall time is composition, layout and draw of the rows on screen.
 * - the harness cost per frame is the same for both lists — the pass DIFFERENCE is scroll work in frames.
 * - the NJ pass loads the legacy fixture and runs the production `\W/` search — matched words have variant slashes.
 * - the height pass builds a synthetic sample of all row shapes — half one-line, half multi-line, interleaved.
 * - interleaving makes a recycled row composition change height almost every time.
 * - the kind pass interleaves the four row kinds, all one-line — only the icon differs.
 * - `PERF scroll-*` lines carry the numbers — the budget assertion fails loudly on regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemListScrollPerformanceTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase
	private val descLinesWas = ItemRow.descLines

	/** The host list — a swap exchanges the measured population. */
	private val state = mutableStateOf(ItemListState())

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
		ItemRow.descLines = descLinesWas
		db.close()
	}

	companion object {
		/** One hand-stepped frame of the fling, in virtual time. */
		private const val FRAME_MS = 16L

		/**
		 * The swipe's end velocity — above every fling clamp, so each list
		 * flings at the system maximum: the fastest scroll possible.
		 */
		private const val FLING_VELOCITY = 15_000f

		/** Frames stepped per fling before the end check runs again. */
		private const val FRAMES_PER_FLING = 90

		/** Consecutive near-empty frames that mean a fling settled. */
		private const val SETTLED_STREAK = 4

		/** A fling guard — a pass that cannot reach the end fails, not hangs. */
		private const val MAX_FLINGS = 40

		/** Both measured NJ passes together must stay under this wall clock. */
		private const val NJ_BUDGET_MS = 10_000.0

		/** All three measured height passes together must stay under this wall clock. */
		private const val HEIGHTS_BUDGET_MS = 40_000.0

		/** Both measured kind passes together must stay under this wall clock. */
		private const val KINDS_BUDGET_MS = 20_000.0
	}

	/** The NJ words whose names carry a variant slash — the production `\W/` search over the fixture. */
	private fun njVariantRows(): List<ItemUiModel> {
		val dir = File(javaClass.getResource("/perf/legacy/NJ")!!.toURI())
		val subject = LegacyJsonLoader.parseSubjectDir(
			LegacyFileSource { rel -> File(dir, rel).takeIf { it.isFile && it.length() > 0 }?.readText() },
			"NJ",
		) ?: throw IllegalStateException("missing legacy NJ fixture")
		ItemRepository.import(LegacySubjectImporter(subject))
		val nj = ItemRepository.subjects().single()
		val index = SearchIndex(
			ItemRepository.subgraph(nj.id),
			ItemRepository.subgraphLinks(nj.id),
			ItemRepository.subgraphChildLinks(nj.id),
		)
		val rows = ArrayList<ItemRow>()
		SearchEngine("\\W/").search(index, listOf(nj)) { item, hitPath ->
			rows.add(ItemRow.ofSearchHit(item, hitPath, rows.size + 1, index.linksOf(item.id)))
		}
		return rows.map { it.toUi() }
	}

	/**
	 * The height control — every row the same two-line shape, so the mixed
	 * pass's extra cost separates "rows are tall" from "heights change".
	 */
	private fun uniformTwoLineRows(): List<ItemUiModel> = List(300) { i ->
		ItemUiModel(
			key = "uni$i",
			position = i + 1,
			name = "(an der ersten Stelle $i)\n(auf dem ersten Platz)",
			description = "",
			kind = RowKind.WORD,
			ratio = 0,
			isReference = false,
			selected = false,
		)
	}

	/**
	 * A kind-mixed list — subjects, chapters, words and notes interleaved,
	 * all one-line — so the only per-row difference is the kind (its icon).
	 */
	private fun mixedKindRows(): List<ItemUiModel> {
		val kinds = listOf(RowKind.SUBJECT, RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE)
		return List(300) { i ->
			ItemUiModel(
				key = "kind$i",
				position = i + 1,
				name = "name $i",
				description = "",
				kind = kinds[i % 4],
				ratio = 0,
				isReference = false,
				selected = false,
			)
		}
	}

	/** The comparison floor — one line, no description, the same row count. */
	private fun oneLineRows(count: Int): List<ItemUiModel> = List(count) { i ->
		ItemUiModel(
			key = "base$i",
			position = i + 1,
			name = "wort ${i + 1}",
			description = "",
			kind = RowKind.WORD,
			ratio = 0,
			isReference = false,
			selected = false,
		)
	}

	/**
	 * Half one-line, half multi-line, interleaved — a recycled row composition
	 * lands on content of a different height almost every time, which is what
	 * a real mixed list does to the rows' size springs.
	 */
	private fun variableHeightRows(): List<ItemUiModel> {
		val wrapName = "Die Landesverteidigung hat den Auftrag die Souveränität " +
			"und die territoriale Integrität des Landes zu schützen"
		val longDesc = "pl. -en/-s/Konti, pl. äu-e / -e, pl. ü-er/-s, " +
			"3. p. enthält, formell, neutral, informell"
		val multi = listOf(
			{ i: Int -> "kurz $i" to "pl. -e" },
			{ i: Int -> "kurz $i" to longDesc },
			{ i: Int -> "(an der ersten Stelle $i)\n(auf dem ersten Platz)" to "" },
			{ i: Int -> "(der Karneval/Fasching $i)\n(die Fasnacht/Fastnacht)\n(das Fest)" to "" },
			{ i: Int -> "$wrapName $i" to "" },
		)
		return List(300) { i ->
			val (name, description) =
				if (i % 2 == 0) "kurz $i" to "" else multi[(i / 2) % multi.size](i)
			ItemUiModel(
				key = "var$i",
				position = i + 1,
				name = name,
				description = description,
				kind = RowKind.WORD,
				ratio = i * 37 % 100,
				isReference = false,
				selected = false,
			)
		}
	}

	/** Composes the host list once — every pass swaps [state] instead. */
	private fun composeHost(reorderable: Boolean) {
		rule.setContent {
			// the test window is 320x480 — the column fills it, a phone-height viewport
			Column(Modifier.size(320.dp, 480.dp)) {
				ItemList(state = state.value, callbacks = ItemListCallbacks(), reorderable = reorderable)
			}
		}
		rule.waitForIdle()
	}

	private fun list() = rule.onNode(hasScrollToIndexAction())

	/**
	 * One population's measurement — swaps [models] in, flings once unmeasured
	 * for the JIT, then runs [passes] measured fling passes, top to end.
	 */
	private fun flingThrough(
		models: List<ItemUiModel>,
		variant: ExplorerRowVariant,
		reorderable: Boolean,
		label: String,
		passes: Int = 3,
	): List<Double> {
		state.value = ItemListState(
			items = models, maxPosition = models.size, variant = variant)
		rule.waitForIdle()
		list().performScrollToIndex(0)
		flingToEnd(models.last().name)
		val frames = ArrayList<Double>()
		var flings = 0
		repeat(passes) {
			list().performScrollToIndex(0)
			flings += flingToEnd(models.last().name, frames)
		}
		assertTrue("$label never reached its last row", flings > 0)
		assertTrue("$label measured no working frames", frames.isNotEmpty())
		report(label, models.size, flings, frames)
		return frames
	}

	/**
	 * Flings to the row named [lastName] at maximum velocity, stepping the
	 * clock one frame at a time. Frames with work land in [frames] when
	 * given; a frame cheaper than 0.5ms four times in a row ends a settled
	 * fling, and such idle frames never count.
	 */
	private fun flingToEnd(lastName: String, frames: ArrayList<Double>? = null): Int {
		var flings = 0
		while (rule.onAllNodesWithText(lastName).fetchSemanticsNodes().isEmpty()) {
			assertTrue("the pass cannot reach $lastName", flings < MAX_FLINGS)
			flings++
			rule.mainClock.autoAdvance = false
			rule.onRoot().performTouchInput {
				swipeWithVelocity(
					start = Offset(center.x, bottom - 1f),
					end = Offset(center.x, top + 1f),
					endVelocity = FLING_VELOCITY,
					durationMillis = 50,
				)
			}
			var settled = 0
			var frame = 0
			while (frame < FRAMES_PER_FLING && settled < SETTLED_STREAK) {
				val start = System.nanoTime()
				rule.mainClock.advanceTimeBy(FRAME_MS)
				Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
				rule.waitForIdle()
				val ms = (System.nanoTime() - start) / 1_000_000.0
				if (ms < 0.5) settled++ else settled = 0
				if (ms >= 0.5) frames?.add(ms)
				frame++
			}
			rule.mainClock.autoAdvance = true
			rule.waitForIdle()
		}
		return flings
	}

	/** The pass's numbers — average, 95th percentile, worst frames, total. */
	private fun report(label: String, rows: Int, flings: Int, frames: List<Double>) {
		val sorted = frames.sorted()
		val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]
		val worst = sorted.takeLast(5).reversed()
			.joinToString(", ") { ms -> "${fixed1(ms)}ms@${frames.indexOf(ms)}" }
		println(
			"PERF scroll-$label: rows=$rows flings=$flings frames=${frames.size}" +
				" avg=${fixed1(frames.average())}ms p95=${fixed1(p95)}ms" +
				" max=${fixed1(sorted.last())}ms total=${fixed1(frames.sum())}ms worst=[$worst]")
	}

	/**
	 * The verdict — the target's frame cost over the one-line floor, and the
	 * overtime as a count of lost frames across the whole scroll.
	 */
	private fun reportLostFrames(label: String, target: List<Double>, base: List<Double>) {
		assertTrue("no frames measured for $label", target.isNotEmpty() && base.isNotEmpty())
		println(
			"PERF scroll-$label-vs-one-line:" +
				" frameRatio=${fixed2(target.average() / base.average())}x" +
				" totalTarget=${fixed1(target.sum())}ms totalBase=${fixed1(base.sum())}ms" +
				" lostFrames=${fixed1((target.sum() - base.sum()) / FRAME_MS)}")
	}

	private fun fixed1(value: Double) = String.format(Locale.ROOT, "%.1f", value)
	private fun fixed2(value: Double) = String.format(Locale.ROOT, "%.2f", value)

	@Test
	fun `fast-scrolling kind-mixed rows stays under its time budget`() {
		val target = mixedKindRows()
		assertEquals(300, target.size)
		composeHost(reorderable = true)

		val targetFrames = flingThrough(
			target, ExplorerRowVariant.Browse, reorderable = true, label = "kinds")
		val baseFrames = flingThrough(
			oneLineRows(target.size), ExplorerRowVariant.Browse, reorderable = true, label = "kinds-floor")

		reportLostFrames("kinds", targetFrames, baseFrames)
		assertTrue(
			"scrolling the kind-mixed rows took ${fixed1(targetFrames.sum() + baseFrames.sum())}ms" +
				" (budget $KINDS_BUDGET_MS ms)",
			targetFrames.sum() + baseFrames.sum() < KINDS_BUDGET_MS)
	}

	@Test
	fun `fast-scrolling the NJ variant-word results stays under its time budget`() {
		ItemRow.descLines = Int.MAX_VALUE
		val target = njVariantRows()
		// NJ's word rows carrying a variant slash — 138 after the subject-wide name merge
		assertEquals(138, target.size)
		composeHost(reorderable = false)

		val targetFrames = flingThrough(
			target, ExplorerRowVariant.Search, reorderable = false, label = "nj-variants")
		val baseFrames = flingThrough(
			oneLineRows(target.size), ExplorerRowVariant.Search, reorderable = false, label = "nj-floor")

		reportLostFrames("nj-variants", targetFrames, baseFrames)
		assertTrue(
			"scrolling the NJ variant words took ${fixed1(targetFrames.sum() + baseFrames.sum())}ms" +
				" (budget $NJ_BUDGET_MS ms)",
			targetFrames.sum() + baseFrames.sum() < NJ_BUDGET_MS)
	}

	@Test
	fun `fast-scrolling variable-height rows stays under its time budget`() {
		ItemRow.descLines = Int.MAX_VALUE
		val target = variableHeightRows()
		assertEquals(300, target.size)
		composeHost(reorderable = true)

		val controlFrames = flingThrough(
			uniformTwoLineRows(), ExplorerRowVariant.Browse, reorderable = true, label = "heights-uniform")
		val targetFrames = flingThrough(
			target, ExplorerRowVariant.Browse, reorderable = true, label = "heights")
		val baseFrames = flingThrough(
			oneLineRows(target.size), ExplorerRowVariant.Browse, reorderable = true, label = "heights-floor")

		reportLostFrames("heights", targetFrames, baseFrames)
		reportLostFrames("heights-uniform", controlFrames, baseFrames)
		val total = targetFrames.sum() + controlFrames.sum() + baseFrames.sum()
		assertTrue(
			"scrolling the variable-height rows took ${fixed1(total)}ms" +
				" (budget $HEIGHTS_BUDGET_MS ms)",
			total < HEIGHTS_BUDGET_MS)
	}
}
