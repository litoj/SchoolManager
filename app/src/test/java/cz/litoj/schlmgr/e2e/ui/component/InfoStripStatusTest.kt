package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.ExplorerTopBar
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - a selection status edits the info count line and keeps the container properties below it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InfoStripStatusTest {

	@get:Rule
	val rule = createComposeRule()

	private val status = mutableStateOf<String?>(null)

	private fun show() {
		rule.setContent {
			ExplorerTopBar(
				breadcrumbs = listOf("A"),
				onBreadcrumbClick = {},
				info = InfoBar.Children(
					kinds = mapOf(ItemKind.CHAPTER to 2),
					ratio = 50,
					description = "First property\nSecond property",
				),
				isInfoExpanded = false,
				onInfoClick = {},
				onFilterEdit = {},
				onFilterDrop = {},
				onParentClick = {},
				statusLine = status.value,
			)
		}
		rule.waitForIdle()
	}

	@Test
	fun `a selection status keeps the container properties`() {
		show()
		rule.onNodeWithText("First property", substring = true).assertExists()
		rule.onNodeWithText("Second property", substring = true).assertExists()

		rule.runOnIdle { status.value = "Selected: 1" }
		rule.waitForIdle()

		rule.onNodeWithText("Selected: 1", substring = true).assertExists()
		rule.onNodeWithText("First property", substring = true).assertExists()
		rule.onNodeWithText("Second property", substring = true).assertExists()
	}
}
