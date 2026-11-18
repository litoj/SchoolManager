package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.component.CrumbChain
import cz.litoj.schlmgr.ui.component.ExplorerTopBar
import org.junit.Assert.assertEquals
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

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	private fun show(names: List<String>) {
		rule.setContent {
			ExplorerTopBar(
				breadcrumbs = names,
				onBreadcrumbClick = {},
				info = null,
				isInfoExpanded = false,
				onInfoClick = {},
				onFilterEdit = {},
				onFilterDrop = {},
				onParentClick = {},
			)
		}
	}

	private fun upLabel() = RuntimeEnvironment.getApplication().getString(R.string.crumb_parent)

	@Test
	fun `the root has no up symbol`() {
		show(emptyList())
		rule.onNodeWithContentDescription(upLabel()).assertDoesNotExist()
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
