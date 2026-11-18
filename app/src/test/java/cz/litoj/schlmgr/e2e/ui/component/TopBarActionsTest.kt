package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.TopBarActions
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

	private fun show(count: Int, width: androidx.compose.ui.unit.Dp = 320.dp) {
		show(List(count) { MenuEntry(it, R.string.search, icon = R.drawable.ic_search) }, width)
	}

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

	@Test
	fun `one action is direct and has no more`() {
		show(1)

		rule.onNodeWithContentDescription("More").assertDoesNotExist()
		assertEquals(1, rule.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().size)
	}

	@Test
	fun `all fitting actions show directly`() {
		show(2)

		rule.onNodeWithContentDescription("More").assertDoesNotExist()
		assertEquals(2, rule.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().size)
	}

	@Test
	fun `overflow leaves a real more menu`() {
		show(5)

		rule.onNodeWithContentDescription("More").assertExists()
		assertEquals(1, rule.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().size)
	}

	@Test
	fun `even no direct icons keep the more menu`() {
		show(3, width = 230.dp)

		rule.onNodeWithContentDescription("More").assertExists()
		assertEquals(0, rule.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().size)
	}

	@Test
	fun `the weighted title starts after navigation and keeps its minimum space`() {
		rule.setContent {
			Row(Modifier.width(320.dp)) {
				TopBarNav(onNavigate = {}, contentDescription = "back")
				TopBarTitle("Set")
			}
		}

		val nav = rule.onNodeWithTag("top_bar_nav").getUnclippedBoundsInRoot()
		val title = rule.onNodeWithTag("top_bar_title").getUnclippedBoundsInRoot()
		rule.runOnIdle {
			assertEquals(nav.right, title.left)
			assertTrue(title.right - title.left >= 96.dp)
		}
	}

	@Test
	fun `no action shows no button`() {
		show(0)

		rule.onNodeWithContentDescription("More").assertDoesNotExist()
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
	fun `a lone action without an icon shows no icon button`() {
		show(listOf(MenuEntry(1, R.string.more_import)))

		rule.onNodeWithContentDescription("Import").assertDoesNotExist()
		rule.onNodeWithContentDescription("More").assertExists()
		openMore()
		rule.onNodeWithText("Import").assertExists()
	}
}
