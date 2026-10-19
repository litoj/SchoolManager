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
 * Compose tests for the explorer list's reorder drag.
 * - the dragged row stays an ordinary list row — drawn once under the finger.
 * - a self-drawn copy fails.
 * - a reorder from the top row moves the row a slot down instead of scrolling.
 * - the press's selection republish leaves the armed drag alone.
 * - a round trip onto the row's own slot keeps the selection — no drop fires.
 *
 * Every assertion reads the rendered rows, or the host's recorded steps, selection flags and published state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemListDragTest {

	@get:Rule
	val rule = createComposeRule()

	/** The live reorder steps the list reported, in order. */
	private val steps = mutableListOf<Pair<Int, Int>>()

	/** The rows the host keeps in order — every reorder step is applied to it. */
	private val items = mutableStateListOf<ItemUiModel>()

	/** The state the last composition published — how a test reads the republish back. */
	private var published = ItemListState()

	/** `count` unselected subject rows named `item n`, in list order. */
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

	/** Composes a reorderable list of [count] rows named `item n`, all visible at once. */
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

	/**
	 * The host's live reorder step, exactly as the explorer's view model applies it — the
	 * row leaves [from] and lands on [to], and the positions are renumbered. Without
	 * this the list would only hear the step and the dragged row would never move.
	 */
	private fun moveRow(from: Int, to: Int) {
		steps += from to to
		items.add(to, items.removeAt(from))
		for (i in items.indices) items[i] = items[i].copy(position = i + 1)
	}

	/** The rendered bounds of the one row named [name]. */
	private fun row(name: String): DpRect = rule.onNodeWithText(name).getBoundsInRoot()

	/** The vertical center of the row named [name]. */
	private fun centerY(name: String): Dp = row(name).let { it.top + (it.bottom - it.top) / 2 }

	/**
	 * Long-presses the row named [name] — the press arms the drag — and moves
	 * the finger onto the row named [onto]: the first move past the slop claims
	 * the drag, the next one steps it.
	 *
	 * @return the finger's x in px — a later leg of the same drag stays on it
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
	 * Long-presses the row named [name], drags it onto the row named [onto] and calls
	 * [check] while the finger is still down — the moment the defect was reported at —
	 * with the dragged row's bounds and the finger's y. The release ends the drag.
	 */
	private fun drag(name: String, onto: String, check: (DpRect, Dp) -> Unit) {
		val toY = centerY(onto)
		pressAndDrag(name, onto)
		check(row(name), toY)
		rule.onRoot().performTouchInput { up() }
		settle()
	}

	/**
	 * Runs the frames the drag needs by hand — the clock cannot auto-advance, because the
	 * list's auto-scroll loop never stops asking for frames while the finger is down.
	 */
	private fun settle() {
		rule.mainClock.advanceTimeBy(1000)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
		rule.waitForIdle()
	}

	/**
	 * Composes a reorderable list of [count] rows whose long-press starts a
	 * selection — the production press shape: the press arms the pre-selection
	 * drag mode, and the selection's republish must not eat the arm.
	 * - the rows live in a state list the composition reads, so a selection
	 *   toggle or a reorder step recomposes a fresh [ItemListState] — the
	 *   republish the explorer's view model performs.
	 * - the fresh state keeps [ItemListState.contentStep], which [ItemList]'s
	 *   arm reset keys on — a bumped step would eat the arm and silently
	 *   turn these into plain reorder tests.
	 * - each composition records its state in [published], so a test reads
	 *   the republish back.
	 */
	private fun showSelecting(count: Int, onDrop: () -> Unit) {
		items += rows(count)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				val state = ItemListState(
					items = items.toList(),
					maxPosition = items.size,
					selectionActive = items.any { it.selected },
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

	/** The production drop's selection clear, as the harness applies it. */
	private fun clearSelection() {
		for (i in items.indices) items[i] = items[i].copy(selected = false)
	}

	/**
	 * Long-presses the row named [name], drags it onto the row named [onto]
	 * and back onto the pressed row's own slot, then releases. The slot keeps
	 * the press's y — held after the first leg by the row that slid up into it.
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
	 * The production press republishes the list — the long-press arms the drag
	 * with the pre-selection mode and then toggles the row's selection, whose
	 * remap lands as a new list instance. The arm must survive its own press —
	 * a reset eating it kills both drag modes.
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
								selectionActive = true,
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
	 * The round-trip fallback — a reorder drag ending back on the row's own
	 * slot is indistinguishable from a long-press meant to select: no drop
	 * fires (so no persist, no selection clear) and the selection stands.
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
}
