package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
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

	private var parents = 0

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		parents = 0
	}

	private fun show(names: List<String>) {
		rule.setContent {
			ExplorerTopBar(
				breadcrumbs = names,
				onBreadcrumbClick = {},
				info = null,
				isInfoExpanded = false,
				onInfoClick = {},
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
		show(listOf("A"))
		val bounds = rule.onNodeWithContentDescription(upLabel()).getUnclippedBoundsInRoot()
		assertEquals("the slot stacks exactly on the 25dp line", 25.dp,
			bounds.bottom - bounds.top)
	}
}
