package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipConflict
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.FlipConflictPopup
import cz.litoj.schlmgr.ui.popup.InfoPopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the title info icon opens the help above the conflict round.
 * - the help shows the round logic and OK closes only the help.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlipConflictPopupHelpTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun round() = FlipConflictPopup(listOf(
		FlipConflict(
			DbItem(id = 1, name = "pes", description = "", type = ItemKind.TRANSLATION),
			outside = emptyList(), scheduled = emptyList())))

	private fun text(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	@Test
	fun `the title info icon opens the help`() {
		val popup = round()
		rule.setContent { popup.Content() }

		rule.onNodeWithContentDescription("Help").performClick()

		rule.runOnIdle {
			assertTrue(Dialogs.isShowing<FlipConflictPopup>())
			assertTrue(Dialogs.isShowing<InfoPopup>())
		}
	}

	@Test
	fun `the help shows the round logic and OK closes only the help`() {
		round()
		rule.setContent {
			// the test renders the stack itself, since no dialog host runs here
			val stack by Dialogs.stack.collectAsState()
			stack.forEach { it.Content() }
		}

		rule.onNodeWithContentDescription("Help").performClick()

		rule.onNodeWithText("Each row is a word or a translation", substring = true).assertExists()
		// the conflict round and the help each carry one OK, and the help sits on top
		val ok = rule.onAllNodesWithText(text(R.string.ok))
		assertEquals("both popups show their own OK", 2, ok.fetchSemanticsNodes().size)
		ok[1].performClick()

		rule.runOnIdle {
			assertFalse(Dialogs.isShowing<InfoPopup>())
			assertTrue(Dialogs.isShowing<FlipConflictPopup>())
		}
	}
}
