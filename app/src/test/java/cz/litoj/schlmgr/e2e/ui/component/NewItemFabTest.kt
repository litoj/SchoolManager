package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.component.FabMargin
import cz.litoj.schlmgr.ui.component.FabSize
import cz.litoj.schlmgr.ui.component.FabVisibility
import cz.litoj.schlmgr.ui.component.NewItemFab
import cz.litoj.schlmgr.ui.component.PaletteRadius
import cz.litoj.schlmgr.ui.component.fabDragVisibility
import cz.litoj.schlmgr.ui.component.paletteOffset
import cz.litoj.schlmgr.ui.component.rememberFabVisibility
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - one offered kind commits on the release, wherever the finger ends.
 * - several kinds fan out a palette.
 * - a drag onto an icon or a standing tap commits a palette entry.
 * - a plain tap leaves the palette standing for the click on the icon.
 * - the visibility follows the drag gesture, not the list scroll, so it
 *   also hides with nothing to scroll.
 * - a sideways swipe that climbs a little keeps the button.
 * - a reversal inside one gesture resets its travel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewItemFabTest {

	@get:Rule
	val rule = createComposeRule()

	private val picked = ArrayList<RowKind>()

	private fun setContent(kinds: List<RowKind>) {
		rule.setContent {
			NewItemFab(
				kinds = kinds,
				visible = true,
				onPick = { picked.add(it) },
				modifier = Modifier.fillMaxSize(),
			)
		}
		rule.waitForIdle()
	}

	/** The center of the button at the bottom end, at its own margin and half size. */
	private fun TouchInjectionScope.fabCenter(): Offset {
		val margin = with(rule.density) { FabMargin.toPx() }
		val half = with(rule.density) { FabSize.toPx() } / 2
		return Offset(width - margin - half, height - margin - half)
	}

	private fun TouchInjectionScope.iconAt(index: Int, count: Int): Offset =
		fabCenter() + paletteOffset(index, count, with(rule.density) { PaletteRadius.toPx() })

	@Test
	fun `a single kind commits on a plain tap`() {
		setContent(listOf(RowKind.SUBJECT))

		rule.onRoot().performTouchInput { click(fabCenter()) }

		assertEquals(listOf(RowKind.SUBJECT), picked)
	}

	@Test
	fun `a single kind commits after a drag away`() {
		setContent(listOf(RowKind.SUBJECT))

		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-200f, -200f))
			up()
		}

		assertEquals("the release commits wherever the finger ends",
			listOf(RowKind.SUBJECT), picked)
	}

	@Test
	fun `a plain tap leaves the palette standing, the icon's click commits`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the tap on the button releases off the icons, so nothing commits yet
		rule.onRoot().performTouchInput { click(fabCenter()) }
		assertTrue("the plain tap commits nothing", picked.isEmpty())

		rule.onRoot().performTouchInput { click(iconAt(2, 3)) }

		assertEquals(listOf(RowKind.NOTE), picked)
	}

	@Test
	fun `a short drag toward the icon commits it`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the drag leaves the button toward the word icon but stops far short of
		// it, so the direction alone picks
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-25f, -25f))
			up()
		}

		assertEquals(listOf(RowKind.WORD), picked)
	}

	@Test
	fun `a drag from the button's corner past half its size commits`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the press lands at the bottom-right corner of the button, and the drag
		// runs up past half of it and releases inside
		rule.onRoot().performTouchInput {
			val press = fabCenter() + Offset(15f, 15f)
			down(press)
			moveTo(press + Offset(0f, -30f))
			up()
		}

		assertEquals(listOf(RowKind.CHAPTER), picked)
	}

	@Test
	fun `a drag under half the button stays a tap`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the small move never crosses half of the button, so the release is a
		// plain tap and nothing commits
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-12f, -12f))
			up()
		}

		assertTrue(picked.isEmpty())
	}

	/** The visibility wired to a plain draggable area, no list involved. */
	private fun visibilityArea(): FabVisibility {
		lateinit var visibility: FabVisibility
		rule.setContent {
			visibility = rememberFabVisibility()
			Box(Modifier.fillMaxSize().fabDragVisibility(visibility))
		}
		rule.waitForIdle()
		return visibility
	}

	@Test
	fun `the button hides on a gesture up and returns on a gesture down`() {
		val visibility = visibilityArea()
		assertTrue("idle at the start shows the button", visibility.shown)

		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		assertTrue("a gesture up hides the button", !visibility.shown)

		rule.onRoot().performTouchInput { swipeDown() }
		rule.waitForIdle()
		assertTrue("a gesture down shows the button again", visibility.shown)
	}

	@Test
	fun `a twitch keeps the button, one covering gesture drops it`() {
		val visibility = visibilityArea()
		val twitch = with(rule.density) { 5.dp.toPx() }

		// a twitch under the margin keeps the button, each gesture counts alone
		rule.onRoot().performTouchInput {
			down(center)
			moveBy(Offset(0f, -twitch))
			up()
		}
		rule.waitForIdle()
		assertTrue("the twitch keeps the button", visibility.shown)

		rule.onRoot().performTouchInput {
			down(center)
			moveBy(Offset(0f, -twitch * 4f))
			up()
		}
		rule.waitForIdle()
		assertTrue("the covering gesture hides the button", !visibility.shown)

		rule.onRoot().performTouchInput {
			down(center)
			moveBy(Offset(0f, twitch))
			up()
		}
		rule.waitForIdle()
		assertTrue("any gesture down shows the button again", visibility.shown)
	}

	@Test
	fun `a sideways swipe that climbs a little keeps the button`() {
		val visibility = visibilityArea()

		// the sweep runs left far more than it climbs, so no move reads as a climb
		rule.onRoot().performTouchInput {
			down(center)
			repeat(30) { moveBy(Offset(-8.dp.toPx(), -1.dp.toPx())) }
			up()
		}
		rule.waitForIdle()
		assertTrue("the sibling swipe keeps the button", visibility.shown)
	}

	@Test
	fun `a reversal inside one gesture resets the travel`() {
		val visibility = visibilityArea()
		val step = with(rule.density) { 8.dp.toPx() }

		// a climb, a retreat and a climb again. the gesture ends on the climb,
		// so a missing reset would cover the margin whole. each move rides its
		// own injection, one frame batches them otherwise
		rule.onRoot().performTouchInput { down(center) }
		rule.waitForIdle()
		rule.onRoot().performTouchInput { moveBy(Offset(0f, -step)) }
		rule.waitForIdle()
		rule.onRoot().performTouchInput { moveBy(Offset(0f, step / 2f)) }
		rule.waitForIdle()
		rule.onRoot().performTouchInput { moveBy(Offset(0f, -step)) }
		rule.waitForIdle()
		rule.onRoot().performTouchInput { up() }
		rule.waitForIdle()
		assertTrue("the reversal resets the travel of the gesture", visibility.shown)

		// one clean gesture of the same length still covers the margin
		rule.onRoot().performTouchInput {
			down(center)
			moveBy(Offset(0f, -step * 3f))
			up()
		}
		rule.waitForIdle()
		assertTrue("the clean gesture hides the button", !visibility.shown)
	}

	@Test
	fun `a short list keeps the button through a covering gesture`() {
		lateinit var visibility: FabVisibility
		val list = LazyListState()
		rule.setContent {
			visibility = rememberFabVisibility()
			// three rows fit the viewport, so the list has nothing to scroll
			LazyColumn(
				Modifier.fillMaxSize().fabDragVisibility(visibility) {
					list.canScrollForward || list.canScrollBackward
				},
				state = list,
			) {
				items(3) { i ->
					Text("row $i", Modifier.height(40.dp))
				}
			}
		}
		rule.waitForIdle()
		assertTrue(visibility.shown)

		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		assertTrue("a list that fits the viewport keeps the button", visibility.shown)
	}

	@Test
	fun `a drag into the gap between two icons picks the harder one`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the direction runs into the gap between the word and the note, past the
		// middle of the gap, and two thirds of the word still take it
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-94f, -34f))
			up()
		}

		assertEquals(listOf(RowKind.WORD), picked)
	}

	@Test
	fun `a drag along the bottom commits the leftmost icon`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the sweep runs below the center line of the button, off the arc of the
		// icons, and the direction still points at the note
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-30f, 60f))
			moveTo(fabCenter() + Offset(-250f, 60f))
			up()
		}

		assertEquals(listOf(RowKind.NOTE), picked)
	}
}
