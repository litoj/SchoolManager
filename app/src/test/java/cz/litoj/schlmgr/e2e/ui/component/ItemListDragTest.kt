package cz.litoj.schlmgr.e2e.ui.component

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.component.ItemList
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.TranslationLine
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * - the dragged row stays an ordinary list row, drawn once under the finger.
 * - a self-drawn copy fails.
 * - every assertion reads the rendered rows or the records of the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemListDragTest {

	@get:Rule
	val rule = createComposeRule()

	/** The live reorder steps the list reported, in order. */
	private val steps = mutableListOf<Pair<Int, Int>>()

	/** The rows kept in order by the host, to which every reorder step is applied. */
	private val items = mutableStateListOf<ItemUiModel>()

	/** The state published by the last composition, the way a test reads the republish back. */
	private var published = ItemListState()

	private fun rows(count: Int): List<ItemUiModel> = List(count) { i ->
		ItemUiModel(
			key = "k$i",
			position = i + 1,
			name = "item ${i + 1}",
			description = "",
			kind = RowKind.SUBJECT,
			ratio = 0,
			isReference = false,
			selected = false,
		)
	}

	/** A flipped word row of four translation lines, several times taller than a plain row. */
	private fun tallRow(position: Int): ItemUiModel = ItemUiModel(
		key = "tall",
		position = position,
		name = "tall",
		description = "",
		kind = RowKind.WORD,
		ratio = 0,
		isReference = false,
		selected = false,
		translations = List(4) { i -> TranslationLine("tall line ${i + 1}", "") },
	)

	private fun show(count: Int) {
		items += rows(count)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onReorderMove = ::moveRow),
					reorderable = true,
				)
			}
		}
	}

	/** Mirrors the live reorder step of the view model, or the dragged row never moves. */
	private fun moveRow(from: Int, to: Int) {
		steps += from to to
		items.add(to, items.removeAt(from))
		for (i in items.indices) items[i] = items[i].copy(position = i + 1)
	}

	private fun row(name: String): DpRect = rule.onNodeWithText(name).getBoundsInRoot()

	private fun centerY(name: String): Dp = row(name).let { it.top + (it.bottom - it.top) / 2 }

	/**
	 * Long-presses the row named [name], then moves the finger onto [onto].
	 * - the press arms the drag.
	 * - the first move past the slop claims it.
	 * - the next move steps it.
	 * - returns the x of the finger in px, where a later leg of the same drag stays.
	 */
	private fun pressAndDrag(name: String, onto: String): Float {
		rule.mainClock.autoAdvance = false
		val from = row(name)
		val px = with(rule.density) { (from.left + (from.right - from.left) / 2).toPx() }
		val startY = with(rule.density) { (from.top + (from.bottom - from.top) / 2).toPx() }
		val endY = with(rule.density) { centerY(onto).toPx() }
		rule.onRoot().performTouchInput {
			down(Offset(px, startY))
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()
		rule.onRoot().performTouchInput {
			moveTo(Offset(px, endY - 1f))
			moveTo(Offset(px, endY))
		}
		settle()
		return px
	}

	/**
	 * Long-presses the row named [name], drags it onto [onto] and calls [check]
	 * while the finger is still down.
	 * - the check receives the bounds of the dragged row and the y of the finger.
	 * - the release ends the drag.
	 */
	private fun drag(name: String, onto: String, check: (DpRect, Dp) -> Unit) {
		val toY = centerY(onto)
		pressAndDrag(name, onto)
		check(row(name), toY)
		rule.onRoot().performTouchInput { up() }
		settle()
	}

	/**
	 * Runs the frames the drag needs by hand.
	 * - the clock cannot auto-advance.
	 * - the auto-scroll loop of the list never stops asking for frames while down.
	 */
	private fun settle() {
		rule.mainClock.advanceTimeBy(1000)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
		rule.waitForIdle()
	}

	/**
	 * Composes a reorderable list of [count] rows whose long-press starts a
	 * selection, the production press shape.
	 * - the press arms the pre-selection drag mode.
	 * - the republish of the selection must not eat the arm.
	 * - the rows live in a state list read by the composition.
	 * - a toggle or a reorder step thus recomposes a fresh [ItemListState].
	 * - the fresh state keeps [ItemListState.contentStep].
	 * - the arm reset of [ItemList] keys on that step.
	 * - a bumped step would eat the arm and turn these into reorder tests.
	 * - each composition records its state in [published].
	 */
	private fun showSelecting(count: Int, onDrop: () -> Unit) {
		items += rows(count)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				val state = ItemListState(
					items = items.toList(),
					maxPosition = items.size,
				)
				published = state
				ItemList(
					state = state,
					callbacks = ItemListCallbacks(
						onItemLongClick = { pressed ->
							val i = items.indexOfFirst { it.key == pressed.key }
							if (i >= 0) items[i] = items[i].copy(selected = true)
						},
						onReorderMove = ::moveRow,
						onReorderDrop = onDrop,
					),
					reorderable = true,
				)
			}
		}
	}

	/** The selection clear of the production drop, as the harness applies it. */
	private fun clearSelection() {
		for (i in items.indices) items[i] = items[i].copy(selected = false)
	}

	/**
	 * Long-presses the row named [name] and drags it onto [onto] and back onto
	 * the slot of the pressed row, then releases.
	 * - the slot keeps the y of the press.
	 * - the row that slid up into it holds that y after the first leg.
	 */
	private fun roundTrip(name: String, onto: String) {
		val startY = centerY(name)
		val px = pressAndDrag(name, onto)
		val backY = with(rule.density) { startY.toPx() }
		rule.onRoot().performTouchInput { moveTo(Offset(px, backY)) }
		settle()
		rule.onRoot().performTouchInput { up() }
		settle()
	}

	@Test
	fun `the dragged row stays one row inside the list, under the finger`() {
		show(8)
		val listTop = row("item 1").top
		drag("item 8", "item 4") { dragged, finger ->
			assertEquals(1, rule.onAllNodesWithText("item 8").fetchSemanticsNodes().size)
			assertTrue(
				"the dragged row must sit under the finger, not above the list:" +
					" dragged=$dragged finger=$finger steps=$steps",
				finger >= dragged.top && finger <= dragged.bottom,
			)
			assertTrue(
				"the dragged row must stay inside the list: dragged=$dragged listTop=$listTop",
				dragged.top >= listTop - 1.dp,
			)
		}
	}

	@Test
	fun `a reorder from the top row moves it a slot instead of scrolling the list`() {
		show(8)
		val first = row("item 1")
		val second = row("item 2")
		drag("item 1", "item 2") { dragged, _ ->
			assertTrue(
				"scrolling the list under the row would leave it at the top:" +
					" dragged=$dragged first=$first second=$second steps=$steps",
				dragged.top >= first.top + (second.top - first.top) / 2f,
			)
		}
		assertEquals("steps=$steps", listOf(0 to 1), steps)
	}

	/**
	 * The production press republishes the list.
	 * - the long-press arms the drag with the pre-selection mode.
	 * - it then toggles the selection of the row.
	 * - that remap lands as a new list instance.
	 * - the arm must survive its own press.
	 * - a reset that eats it kills both drag modes.
	 */
	@Test
	fun `a long-press that republishes the list still drags`() {
		items += rows(3)
		val state = mutableStateOf(ItemListState(items = items.toList(), maxPosition = items.size))
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = state.value,
					callbacks = ItemListCallbacks(
						onItemLongClick = { pressed ->
							state.value = state.value.copy(
								items = state.value.items.map {
									if (it.key == pressed.key) it.copy(selected = true) else it
								},
							)
						},
						onReorderMove = ::moveRow,
					),
					reorderable = true,
				)
			}
		}
		drag("item 1", "item 3") { _, _ -> }
		assertEquals("steps=$steps", listOf(0 to 2), steps)
	}

	/**
	 * The round-trip fallback.
	 * - a reorder drag ending on the slot of the same row matches a long-press.
	 * - no drop fires, so nothing persists and the selection stands.
	 */
	@Test
	fun `a round trip back onto the row's own slot keeps the selection`() {
		var drops = 0
		showSelecting(8) {
			drops++
			clearSelection()
		}

		roundTrip("item 2", "item 4")

		assertEquals("steps=$steps", listOf(1 to 3, 3 to 1), steps)
		assertEquals(0, drops)
		assertTrue("the long-press's selection stands", items[1].selected)
		assertTrue("the selection reached the composed state", published.selectionActive)
	}

	@Test
	fun `a reorder that ends off the row's own slot drops and clears the selection`() {
		var drops = 0
		showSelecting(8) {
			drops++
			clearSelection()
		}

		drag("item 2", "item 4") { _, _ -> }

		assertEquals("steps=$steps", listOf(1 to 3), steps)
		assertEquals(1, drops)
		assertFalse(items.any { it.selected })
		assertFalse("the clear reached the composed state", published.selectionActive)
	}

	/**
	 * The reorder flicker over a tall row.
	 * - the drag holds the finger in the top of a row several lines tall.
	 * - the finger never crosses that row's middle, so no swap may fire.
	 * - a held finger still emits small moves, which the drag replays as wiggles.
	 */
	@Test
	fun `a finger held in the top of a tall row does not flip the order back and forth`() {
		items += rows(4).mapIndexed { i, item -> if (i == 1) tallRow(2) else item }
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onReorderMove = ::moveRow),
					reorderable = true,
				)
			}
		}

		val from = row("item 1")
		val px = with(rule.density) { (from.left + (from.right - from.left) / 2).toPx() }
		val pressY = with(rule.density) { (from.top + (from.bottom - from.top) / 2).toPx() }
		val holdY = with(rule.density) { (row("tall line 1").top + 5.dp).toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(Offset(px, pressY))
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()
		rule.onRoot().performTouchInput {
			moveTo(Offset(px, holdY - 1f))
			moveTo(Offset(px, holdY))
		}
		settle()
		repeat(3) { wiggle ->
			val bump = if (wiggle % 2 == 0) 1f else -1f
			rule.onRoot().performTouchInput { moveTo(Offset(px, holdY + bump)) }
			settle()
		}
		rule.onRoot().performTouchInput { up() }
		settle()

		assertTrue("the finger never crossed the tall row's middle: steps=$steps", steps.isEmpty())
	}
}
