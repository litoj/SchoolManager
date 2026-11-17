package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import androidx.compose.ui.test.onNodeWithTag
import cz.litoj.schlmgr.ui.component.CrumbChain
import cz.litoj.schlmgr.ui.component.CrumbSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performTouchInput
import cz.litoj.schlmgr.ui.component.ExplorerTopBar
import cz.litoj.schlmgr.ui.component.HeaderBandHeight
import cz.litoj.schlmgr.ui.component.plainItemHeight
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The fixed up symbol leaves the row before the root, including from the first crumb. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreadcrumbParentTest {

	@get:Rule
	val rule = createComposeRule()

	private var parents = 0
	private var rowHeight = 0.dp

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		parents = 0
	}

	@After
	fun restoreRowHeight() {
		CrumbSize.select(CrumbSize.NORMAL)
	}

	private fun show(names: List<String>, measureRow: Boolean = false) {
		rule.setContent {
			if (measureRow) rowHeight = plainItemHeight()
			ExplorerTopBar(
				breadcrumbs = names,
				onBreadcrumbClick = {},
				info = null,
				isInfoExpanded = false,
				onInfoClick = {},
				onFilterEdit = {},
				onFilterDrop = {},
				onParentClick = { parents++ },
			)
		}
	}

	private fun upLabel() = RuntimeEnvironment.getApplication().getString(R.string.crumb_parent)

	@Test
	fun `the one crumb above the root also offers its parent`() {
		show(listOf("A"))
		rule.onNodeWithContentDescription(upLabel()).performClick()
		assertEquals(1, parents)
	}

	@Test
	fun `the root has no up symbol`() {
		show(emptyList())
		rule.onNodeWithContentDescription(upLabel()).assertDoesNotExist()
	}

	@Test
	fun `the up slot fills the whole crumb line, so no highlight sits off it`() {
		CrumbSize.select(CrumbSize.COMPACT)
		show(listOf("A"))
		val bounds = rule.onNodeWithContentDescription(upLabel()).getUnclippedBoundsInRoot()
		assertEquals("the slot stacks exactly on the 25dp line", 25.dp,
			bounds.bottom - bounds.top)
	}

	@Test
	fun `the default row preset fills a plain item row`() {
		assertEquals("the app starts with the plain-row mode", CrumbSize.NORMAL, CrumbSize.current)
		show(listOf("A"), measureRow = true)
		val bounds = rule.onNodeWithContentDescription(upLabel()).getUnclippedBoundsInRoot()
		assertTrue("the slot matches its item height",
			abs(rowHeight.value - (bounds.bottom - bounds.top).value) <= 1f)
	}

	@Test
	fun `the spacious preset adds only half the gap to a plain row`() {
		CrumbSize.select(CrumbSize.SPACIOUS)
		show(listOf("A"), measureRow = true)
		val bounds = rule.onNodeWithContentDescription(upLabel()).getUnclippedBoundsInRoot()
		assertTrue("only half of the extra height remains",
			abs((rowHeight + (HeaderBandHeight - rowHeight) / 2).value - (bounds.bottom - bounds.top).value) <= 1f)
	}

	@Test
	fun `the chevron and its gap click as one crumb`() {
		val picked = ArrayList<Int>()
		rule.setContent { CrumbChain(listOf("A", "B"), Color.White, onCrumbClick = picked::add) }
		val slot = rule.onNodeWithTag("crumb-0").getUnclippedBoundsInRoot()
		val x = with(rule.density) { (slot.right - 1.dp).toPx() }
		val y = with(rule.density) { ((slot.top + slot.bottom) / 2).toPx() }

		rule.onRoot().performTouchInput { click(Offset(x, y)) }
		assertEquals(listOf(0), picked)
	}
}
