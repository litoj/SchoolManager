package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
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
}
