package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ExplorerSearchControls
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.TopBarActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TopBarActionsTest {

	@get:Rule
	val rule = createComposeRule()

	private fun show(entries: List<MenuEntry>, width: androidx.compose.ui.unit.Dp = 320.dp) {
		rule.setContent {
			Row(Modifier.height(55.dp)) {
				TopBarActions(entries = entries, width = width, onPick = {})
			}
		}
	}

	private fun openMore() {
		rule.onNode(hasContentDescription("More") and hasClickAction()).performClick()
	}

	private fun showOpenSearchControls() {
		val filter = MenuEntry(
			0, R.string.filter_tag, icon = R.drawable.ic_filter,
			children = listOf(MenuEntry(1, R.string.search_filter)),
		)
		rule.setContent {
			Row(Modifier.height(55.dp)) {
				ExplorerSearchControls(
					title = "Items", query = "", fieldOpen = true, searchBlockedReason = 0,
					entries = listOf(
						MenuEntry(2, R.string.sort, icon = R.drawable.ic_sort_black),
						MenuEntry(3, R.string.more_import, icon = R.drawable.ic_download),
						filter,
					),
					width = 150.dp,
					onOpenSearch = {}, onQueryChange = {}, onQuerySubmit = {},
					onDismissSearch = {}, onMenuPick = {},
				)
			}
		}
	}

	@Test
	fun `the open field pins the filter without a second overflow entry`() {
		showOpenSearchControls()

		rule.onAllNodesWithContentDescription("Filter by").assertCountEquals(1)
		rule.onNodeWithContentDescription("Search").assertDoesNotExist()
		openMore()
		rule.onNodeWithText("Filter by").assertDoesNotExist()
	}

	@Test
	fun `an action without an icon opens the more menu even when it fits`() {
		show(
			listOf(
				MenuEntry(1, R.string.search, icon = R.drawable.ic_search),
				MenuEntry(2, R.string.more_import),
			),
		)

		assertEquals(1, rule.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().size)
		rule.onNodeWithContentDescription("More").assertExists()
		openMore()
		rule.onNodeWithText("Import").assertExists()
	}

	@Test
	fun `an action with a glyph fills a bar slot and needs no menu`() {
		show(
			listOf(
				MenuEntry(1, R.string.search, icon = R.drawable.ic_search),
				MenuEntry(2, R.string.more_import, icon = R.drawable.ic_download),
			),
		)

		rule.onNodeWithContentDescription("Import").assertExists()
		rule.onNodeWithContentDescription("More").assertDoesNotExist()
	}

	@Test
	fun `a bar that holds only some icons keeps them all in more`() {
		show(
			listOf(
				MenuEntry(1, R.string.search, icon = R.drawable.ic_search),
				MenuEntry(2, R.string.more_import, icon = R.drawable.ic_download),
				MenuEntry(3, R.string.sort, icon = R.drawable.ic_sort_black),
			),
		)

		// two slots for three icons, so a partial row would split the list controls
		rule.onNodeWithContentDescription("Search").assertDoesNotExist()
		rule.onNodeWithContentDescription("Import").assertDoesNotExist()
		openMore()
		rule.onNodeWithText("Sort by").assertExists()
	}

	@Test
	fun `a bar too narrow for every icon moves them all into more`() {
		show(
			listOf(
				MenuEntry(1, R.string.search, icon = R.drawable.ic_search),
				MenuEntry(2, R.string.more_import, icon = R.drawable.ic_download),
				MenuEntry(3, R.string.sort, icon = R.drawable.ic_sort_black),
			),
			width = 150.dp,
		)

		rule.onNodeWithContentDescription("Search").assertDoesNotExist()
		openMore()
		rule.onNodeWithText("Search").assertExists()
		rule.onNodeWithText("Import").assertExists()
	}
}
