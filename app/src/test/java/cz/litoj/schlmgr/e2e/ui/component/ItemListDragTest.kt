package cz.litoj.schlmgr.e2e.ui.component

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.component.ItemList
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.RowIconTag
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.StaticRow
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
 * - no source row moves during a reorder, while the counted proxy follows the finger.
 * - an unselected press selects a range, a selected press gathers every selected row.
 * - every assertion reads the rendered rows or the records of the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemListDragTest {

	@get:Rule
	val rule = createComposeRule()

	/** The reorder moves the list reported, at most one per drag, at the drop. */
	private val steps = mutableListOf<Pair<Int, Int>>()

	/** The rows kept in order by the host, which the drop moves once. */
	private val items = mutableStateListOf<StaticRow>()

	/** The state published by the last composition, the way a test reads the republish back. */
	private var published = ItemListState()

	private fun rows(count: Int, selected: Int? = null): List<StaticRow> = List(count) { i ->
		StaticRow(
			key = "k$i",
			position = i + 1,
			name = "item ${i + 1}",
			description = "",
			kind = RowKind.SUBJECT,
			ratio = 0,
			isReference = false,
			selected = i == selected,
		)
	}

	/** A flipped word row of four translation lines, several times taller than a plain row. */
	private fun tallRow(position: Int): StaticRow = StaticRow(
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

	private fun show(
		count: Int,
		selected: Int? = null,
		iconClick: (Int) -> Unit = {},
		rangeTo: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
	) {
		items += rows(count, selected)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(
						onItemIconClick = iconClick,
						onSelectRangeTo = rangeTo,
						onReorderMove = ::moveRow,
					),
					reorderable = true,
				)
			}
		}
	}

	/** Mirrors the selected-row move the drop reports, like the view model does. */
	private fun moveRow(from: Int, to: Int): Boolean {
		if (from !in items.indices) return false
		val moving = items.indices.filter { items[it].selected }
			.ifEmpty { listOf(from) }
			.map { items[it] }
		val remaining = items - moving.toSet()
		if (to !in 0..remaining.size) return false
		val reordered = remaining.toMutableList().apply { addAll(to, moving) }
		if (reordered.indices.all { reordered[it] === items[it] }) return false
		steps += from to to
		items.clear()
		items.addAll(reordered)
		for (i in items.indices) items[i] = items[i].copy(position = i + 1)
		return true
	}

	private fun row(name: String): DpRect = rule.onNodeWithText(name).getBoundsInRoot()

	private fun centerY(name: String): Dp = row(name).let { it.top + (it.bottom - it.top) / 2 }

	/** The center of the type icon of the [index]-th row, in root pixels. */
	private fun iconCenter(index: Int): Offset {
		val bounds = rule.onAllNodesWithTag(RowIconTag, useUnmergedTree = true)[index].getBoundsInRoot()
		return with(rule.density) {
			Offset(
				(bounds.left + (bounds.right - bounds.left) / 2).toPx(),
				(bounds.top + (bounds.bottom - bounds.top) / 2).toPx(),
			)
		}
	}

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
	 * - the check receives the bounds of the pressed row and the y of the finger.
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
	 * Long-presses [name] and parks the finger inside the row [onto] at [fraction] of its height.
	 * - 0 parks at the row's top edge, 1 at its bottom edge.
	 */
	private fun parkOn(name: String, onto: String, fraction: Float) {
		val bounds = row(onto)
		val px = pressAndDrag(name, onto)
		val y = with(rule.density) { (bounds.top + (bounds.bottom - bounds.top) * fraction).toPx() }
		rule.onRoot().performTouchInput { moveTo(Offset(px, y)) }
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
	 * Composes a reorderable list of [count] rows with the pressed row selected.
	 * - a drag of a selected row is the production reorder shape.
	 * - the republish of a selection must not eat the arm.
	 * - the rows live in a state list read by the composition.
	 * - a reorder step thus recomposes a fresh [ItemListState].
	 * - the fresh state keeps [ItemListState.contentStep].
	 * - the arm reset of [ItemList] keys on that step.
	 * - a bumped step would eat the arm and turn these into selection tests.
	 * - each composition records its state in [published].
	 */
	private fun showReorder(count: Int, onDrop: () -> Unit) {
		items += rows(count, selected = 1)
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
							if (!items[pressed].selected) items[pressed] = items[pressed].copy(selected = true)
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
	 * - the list stays frozen, so the press y still is the row's own slot.
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
	fun `a default icon press selects its row and its immediate drag reorders it`() {
		show(5, iconClick = { items[it] = items[it].copy(selected = !items[it].selected) })
		val from = iconCenter(1)
		val toY = with(rule.density) { centerY("item 4").toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput { down(from) }
		rule.waitForIdle()

		assertEquals("no box waits under the finger before the drag", 0,
			rule.onAllNodesWithText("1 item").fetchSemanticsNodes().size)
		assertTrue("the icon press marks its row as a long press would", items.any { it.selected })
		rule.onRoot().performTouchInput { moveTo(Offset(from.x, toY - 1f)) }
		settle()

		assertEquals("the moving box shows when the drag starts", 1,
			rule.onAllNodesWithText("1 item").fetchSemanticsNodes().size)
		rule.onRoot().performTouchInput { moveTo(Offset(from.x, toY)) }
		settle()
		rule.onRoot().performTouchInput { up() }
		settle()

		assertEquals("the immediate icon drag reorders", listOf(1 to 3), steps)
	}

	@Test
	fun `a selected icon press and its drag deselect every covered row`() {
		show(5, selected = 1,
			iconClick = { items[it] = items[it].copy(selected = !items[it].selected) },
			rangeTo = { from, to, on -> for (index in from..to) items[index] = items[index].copy(selected = on) },
		)
		val from = iconCenter(1)
		val toY = with(rule.density) { centerY("item 4").toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput { down(from) }
		rule.waitForIdle()

		assertTrue("the press waits for movement before changing selection", items[1].selected)
		rule.onRoot().performTouchInput {
			moveTo(Offset(from.x, toY - 1f))
			moveTo(Offset(from.x, toY))
		}
		rule.onRoot().performTouchInput { up() }
		settle()

		assertEquals(listOf(false, false, false, false, false), items.map { it.selected })
		assertTrue("the selected icon drag never reorders: steps=$steps", steps.isEmpty())
	}

	@Test
	fun `a tap on the type icon marks the row`() {
		val clicks = mutableListOf<Int>()
		items += rows(3)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onItemIconClick = { clicks += it }),
					reorderable = true,
				)
			}
		}

		rule.onAllNodesWithTag(RowIconTag, useUnmergedTree = true)[1].performClick()

		assertEquals("the icon tap marks its row", listOf(1), clicks)
	}

	@Test
	fun `a selection icon tap toggles only on release`() {
		show(5, selected = 1,
			iconClick = { items[it] = items[it].copy(selected = !items[it].selected) })
		val from = iconCenter(1)
		rule.mainClock.autoAdvance = false

		rule.onRoot().performTouchInput { down(from) }
		rule.waitForIdle()
		assertTrue("the anchor stays selected while the gesture is undecided", items[1].selected)
		rule.onRoot().performTouchInput { up() }
		settle()

		assertFalse("release without a drag toggles the anchor", items[1].selected)
		assertTrue("the tap never reorders: steps=$steps", steps.isEmpty())
	}

	@Test
	fun `a long press on a selected icon reorders instead of painting selection`() {
		val toggles = mutableListOf<Int>()
		items += rows(5, selected = 1)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(
						onItemIconClick = { toggles += it },
						onReorderMove = ::moveRow,
					),
					reorderable = true,
				)
			}
		}
		val from = iconCenter(1)
		val toY = with(rule.density) { centerY("item 4").toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(from)
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()
		rule.onRoot().performTouchInput {
			moveTo(Offset(from.x, toY - 1f))
			moveTo(Offset(from.x, toY))
		}
		settle()

		assertTrue("the icon never toggles the row: $toggles", toggles.isEmpty())
		rule.onRoot().performTouchInput { up() }
		settle()

		assertEquals("steps=$steps", listOf(1 to 3), steps)
	}

	@Test
	fun `a reorder leaves every row still and moves only its proxy`() {
		show(8, selected = 7)
		val start = row("item 8")
		drag("item 8", "item 4") { _, finger ->
			assertEquals("the row stays in the list", start, row("item 8"))
			val proxy = rule.onNodeWithText("1 item").getBoundsInRoot()
			assertTrue("the proxy sits above the finger", proxy.bottom <= finger)
			rule.onNodeWithTag("reorder_target").assertExists()
		}
	}

	@Test
	fun `a reorder from the top row never scrolls the list`() {
		show(8, selected = 0)
		val first = row("item 1")
		drag("item 1", "item 2") { _, _ ->
			assertEquals("the source row keeps its place", first, row("item 1"))
			assertEquals("the proxy names the one item", 1,
				rule.onAllNodesWithText("1 item").fetchSemanticsNodes().size)
		}
		assertEquals("steps=$steps", listOf(0 to 1), steps)
	}

	/**
	 * The selection drag.
	 * - a long-press on an unselected row starts the selection, and the drag covers the range.
	 * - no row moves and no drop persists.
	 */
	@Test
	fun `a drag from an unselected row selects a range instead of moving`() {
		val ranges = mutableListOf<Pair<Int, Int>>()
		var drops = 0
		items += rows(5)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(
						onItemLongClick = { pressed ->
							items[pressed] = items[pressed].copy(selected = true)
						},
						onSelectRange = { from, to -> ranges += from to to },
						onReorderMove = ::moveRow,
						onReorderDrop = { drops++ },
					),
					reorderable = true,
				)
			}
		}
		drag("item 2", "item 4") { _, _ -> }

		assertEquals("the covered range selects", listOf(1 to 3), ranges.distinct())
		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
	}

	/**
	 * The multi-row reorder.
	 * - every selected row moves, including rows with gaps between them.
	 * - the rows keep their shown order and land as one run.
	 * - no source row floats, only the counted proxy moves.
	 */
	@Test
	fun `dragging selected rows gathers every one at the target`() {
		items += rows(6)
		items[0] = items[0].copy(selected = true)
		items[2] = items[2].copy(selected = true)
		items[4] = items[4].copy(selected = true)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onReorderMove = ::moveRow),
					reorderable = true,
				)
			}
		}
		val firstTop = row("item 1").top
		val thirdTop = row("item 3").top
		drag("item 1", "item 6") { _, _ ->
			assertEquals("the source rows keep their places", firstTop, row("item 1").top)
			assertEquals("every source row keeps its place", thirdTop, row("item 3").top)
			assertEquals("the proxy names the whole selection", 1,
				rule.onAllNodesWithText("3 items").fetchSemanticsNodes().size)
		}

		assertEquals("one move lands every selected row: steps=$steps", listOf(0 to 3), steps)
		assertEquals(
			listOf("item 2", "item 4", "item 6", "item 1", "item 3", "item 5"),
			items.map { it.name },
		)
	}

	/**
	 * The production press republishes the list.
	 * - the long-press arms the drag with the pre-selection mode.
	 * - it then selects the row, which republishes the list.
	 * - that remap lands as a new list instance.
	 * - the arm must survive its own press.
	 * - a reset that eats it kills both drag modes.
	 */
	@Test
	fun `a long-press that republishes the list still drags`() {
		items += rows(3, selected = 0)
		val state = mutableStateOf(ItemListState(items = items.toList(), maxPosition = items.size))
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = state.value,
					callbacks = ItemListCallbacks(
						onItemLongClick = { pressed ->
							state.value = state.value.copy(
								items = state.value.items.mapIndexed { i, row ->
									if (i == pressed) (row as StaticRow).copy(selected = true) else row
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
	 * The strip hold.
	 * - a reorder press on a selected row hides the strip while the finger is down.
	 * - the lift shows it again.
	 */
	@Test
	fun `a held press reports the strip hold`() {
		val holds = mutableListOf<Boolean>()
		items += rows(3, selected = 0)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onPressHeld = { holds += it }),
					reorderable = true,
				)
			}
		}
		val from = row("item 1")
		val px = with(rule.density) { (from.left + (from.right - from.left) / 2).toPx() }
		val y = with(rule.density) { (from.top + (from.bottom - from.top) / 2).toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(Offset(px, y))
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()
		assertEquals("the press holds the strip", listOf(true), holds)
		rule.onRoot().performTouchInput { up() }
		settle()
		assertEquals("the lift releases the strip", listOf(true, false), holds)
	}

	/**
	 * The selection press.
	 * - a long-press on an unselected row never holds the strip, so its actions show at once.
	 */
	@Test
	fun `a selection press lets the strip show at once`() {
		val holds = mutableListOf<Boolean>()
		items += rows(3)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(
						onItemLongClick = { pressed ->
							items[pressed] = items[pressed].copy(selected = true)
						},
						onPressHeld = { holds += it },
					),
					reorderable = true,
				)
			}
		}
		val from = row("item 1")
		val px = with(rule.density) { (from.left + (from.right - from.left) / 2).toPx() }
		val y = with(rule.density) { (from.top + (from.bottom - from.top) / 2).toPx() }
		rule.mainClock.autoAdvance = false
		rule.onRoot().performTouchInput {
			down(Offset(px, y))
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()

		assertTrue("the selection press holds nothing: $holds", holds.isEmpty())
		assertTrue("the press selected the row at once", items[0].selected)
		rule.onRoot().performTouchInput { up() }
		settle()
		assertEquals("the lift releases the strip", listOf(false), holds)
	}

	/**
	 * The round-trip fallback.
	 * - a reorder drag ending on the slot of the same row matches a long-press.
	 * - no drop fires, so nothing persists and the selection stands.
	 */
	@Test
	fun `a round trip back onto the row's own slot keeps the selection`() {
		var drops = 0
		showReorder(8) {
			drops++
			clearSelection()
		}

		roundTrip("item 2", "item 4")

		assertTrue("nothing moves before the drop: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertTrue("the long-press's selection stands", items[1].selected)
		assertTrue("the selection reached the composed state", published.selectionActive)
	}

	@Test
	fun `a reorder that ends off the row's own slot drops and clears the selection`() {
		var drops = 0
		showReorder(8) {
			drops++
			clearSelection()
		}

		drag("item 2", "item 4") { _, _ -> }

		assertEquals("steps=$steps", listOf(1 to 3), steps)
		assertEquals(1, drops)
		assertFalse(items.any { it.selected })
		assertFalse("the clear reached the composed state", published.selectionActive)
	}

	@Test
	fun `a drop onto a moving row changes nothing`() {
		var drops = 0
		items += rows(6)
		items[0] = items[0].copy(selected = true)
		items[2] = items[2].copy(selected = true)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onReorderMove = ::moveRow, onReorderDrop = { drops++ }),
					reorderable = true,
				)
			}
		}
		val before = items.map { it.name }

		// the finger ends on a row that itself moves, so the drop reads as a cancel
		drag("item 1", "item 3") { _, _ ->
			rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		}

		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertEquals(before, items.map { it.name })
	}

	/**
	 * The own-slot cancel of a single run.
	 * - the lower half above and the upper half below name the slot the run already fills.
	 * - the marker stays away, so the release is a cancel like a press on a moving row.
	 */
	@Test
	fun `a drop on the run's own slot above changes nothing`() {
		var drops = 0
		showReorder(8) {
			drops++
			clearSelection()
		}

		parkOn("item 2", "item 1", 0.9f)

		rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		rule.onRoot().performTouchInput { up() }
		settle()

		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertTrue("the long-press's selection stands", items[1].selected)
	}

	@Test
	fun `a drop on the run's own slot below changes nothing`() {
		var drops = 0
		showReorder(8) {
			drops++
			clearSelection()
		}

		parkOn("item 2", "item 3", 0.1f)

		rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		rule.onRoot().performTouchInput { up() }
		settle()

		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertTrue("the long-press's selection stands", items[1].selected)
	}

	/** Composes six rows with rows 2 and 4 selected, so the run leaves a hole between them. */
	private fun showGappedRun(onDrop: () -> Unit) {
		items += rows(6)
		items[1] = items[1].copy(selected = true)
		items[3] = items[3].copy(selected = true)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(onReorderMove = ::moveRow, onReorderDrop = onDrop),
					reorderable = true,
				)
			}
		}
	}

	/**
	 * The hole of a gathered run.
	 * - the hole row has a moving row on each of its edges, so both name the run's own place.
	 * - the marker stays away and the release moves nothing.
	 */
	@Test
	fun `a drop at the bottom of the hole row changes nothing`() {
		var drops = 0
		showGappedRun {
			drops++
			clearSelection()
		}
		val before = items.map { it.name }

		parkOn("item 4", "item 3", 0.9f)

		rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		rule.onRoot().performTouchInput { up() }
		settle()

		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertEquals(before, items.map { it.name })
	}

	@Test
	fun `a drop at the top of the row below a gathered run changes nothing`() {
		var drops = 0
		showGappedRun {
			drops++
			clearSelection()
		}
		val before = items.map { it.name }

		parkOn("item 4", "item 5", 0.1f)

		rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		rule.onRoot().performTouchInput { up() }
		settle()

		assertTrue("nothing moves: steps=$steps", steps.isEmpty())
		assertEquals(0, drops)
		assertEquals(before, items.map { it.name })
	}

	/**
	 * The reorder flicker over a tall row.
	 * - the drag holds the finger in the top of a row several lines tall.
	 * - the finger never crosses that row's middle, so no swap may fire.
	 * - a held finger still emits small moves, which the drag replays as wiggles.
	 */
	@Test
	fun `a finger held in the top of a tall row does not flip the order back and forth`() {
		items += rows(4, selected = 0).mapIndexed { i, item -> if (i == 1) tallRow(2) else item }
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

	@Test
	fun `edge auto scroll updates the target under its new geometry`() {
		var drops = 0
		val scrolls = mutableListOf<Pair<Int, Int>>()
		val scroll = LazyListState()
		items += rows(30, selected = 6)
		rule.setContent {
			Column(Modifier.size(300.dp, 400.dp)) {
				ItemList(
					state = ItemListState(items = items.toList(), maxPosition = items.size),
					callbacks = ItemListCallbacks(
						onReorderMove = ::moveRow,
						onReorderDrop = { drops++ },
					),
					modifier = Modifier.size(300.dp, 400.dp),
					reorderable = true,
					listState = scroll,
				)
			}
		}
		rule.mainClock.autoAdvance = false
		rule.waitForIdle()

		parkOn("item 7", "item 7", 0.9f)
		rule.onNodeWithTag("reorder_target").assertDoesNotExist()
		val before = items.map { it.name }

		var scrolled = false
		var frameCount = 0
		while (frameCount < 80 && !scrolled) {
			frame(16)
			frameCount++
			val moved = scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset
			scrolls += moved
			scrolled = (moved.first > 0 || moved.second > 0) && markerShown()
		}
		assertTrue("the list never scrolled: scrolls=$scrolls", scrolled)

		rule.onRoot().performTouchInput { up() }
		settle()

		assertEquals("drops=$drops", 1, steps.size)
		assertEquals(1, drops)
		assertFalse("the scrolled drop kept the old order: $before", before == items.map { it.name })
	}

	private fun markerShown(): Boolean =
		rule.onAllNodesWithTag("reorder_target").fetchSemanticsNodes().isNotEmpty()

	private fun frame(millis: Long) {
		rule.mainClock.advanceTimeBy(millis)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
		rule.waitForIdle()
	}
}
