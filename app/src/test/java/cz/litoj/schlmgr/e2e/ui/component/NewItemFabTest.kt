package cz.litoj.schlmgr.e2e.ui.component

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
import cz.litoj.schlmgr.ui.component.NewItemFab
import cz.litoj.schlmgr.ui.component.PaletteRadius
import cz.litoj.schlmgr.ui.component.paletteOffset
import cz.litoj.schlmgr.ui.component.rememberScrollUpVisible
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The floating New button's interactions on the composed tree — one offered
 * kind commits on the release wherever the finger ends, several fan out a
 * palette that a drag onto an icon or a standing tap commits, and a plain
 * tap leaves the palette standing for the icon's own click.
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

	/** The button's center — bottom end, at its own margin and half size. */
	private fun TouchInjectionScope.fabCenter(): Offset {
		val margin = with(rule.density) { FabMargin.toPx() }
		val half = with(rule.density) { FabSize.toPx() } / 2
		return Offset(width - margin - half, height - margin - half)
	}

	/** The palette icon's center for [index] of [count] around the button. */
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

		// the tap on the button releases off the icons — nothing commits yet
		rule.onRoot().performTouchInput { click(fabCenter()) }
		assertTrue("the plain tap commits nothing", picked.isEmpty())

		// the standing palette's own icon takes the click
		rule.onRoot().performTouchInput { click(iconAt(2, 3)) }

		assertEquals(listOf(RowKind.NOTE), picked)
	}

	@Test
	fun `a short drag toward the icon commits it`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the drag leaves the button toward the word icon but stops far
		// short of it — the direction alone picks
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

		// the press lands at the button's bottom-right corner, the drag runs
		// up past half the button and releases still inside it
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

		// the small move never crosses half the button — the release is a
		// plain tap, nothing commits
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-12f, -12f))
			up()
		}

		assertTrue(picked.isEmpty())
	}

	@Test
	fun `the button hides on the way down and returns on the way up`() {
		val list = LazyListState()
		var shown = true
		rule.setContent {
			shown = rememberScrollUpVisible(list)
			LazyColumn(state = list) {
				items(100) { i ->
					Text("row $i", Modifier.height(40.dp))
				}
			}
		}
		rule.waitForIdle()
		assertTrue("idle at the top shows the button", shown)

		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()
		assertTrue("scrolling down hides the button", !shown)

		rule.onRoot().performTouchInput { swipeDown() }
		rule.waitForIdle()
		assertTrue("scrolling back up shows the button again", shown)
	}

	@Test
	fun `a drag into the gap between two icons picks the harder one`() {
		setContent(listOf(RowKind.CHAPTER, RowKind.WORD, RowKind.NOTE))

		// the direction runs into the gap between the word and the note,
		// past the gap's middle — the word's two thirds still take it
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

		// the sweep runs below the button's center line, off the icons'
		// arc — the direction still points at the note
		rule.onRoot().performTouchInput {
			down(fabCenter())
			moveTo(fabCenter() + Offset(-30f, 60f))
			moveTo(fabCenter() + Offset(-250f, 60f))
			up()
		}

		assertEquals(listOf(RowKind.NOTE), picked)
	}
}
