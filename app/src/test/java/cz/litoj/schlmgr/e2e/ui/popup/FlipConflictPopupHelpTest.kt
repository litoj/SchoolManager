package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipConflict
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.FlipConflictPopup
import cz.litoj.schlmgr.ui.popup.InfoPopup
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
		val popup = round()
		popup.showHelp()
		// taken from the real showHelp path, so the body assert covers what the icon opens
		val help = Dialogs.stack.value.filterIsInstance<InfoPopup>().single()
		rule.setContent { help.Content() }

		rule.onNodeWithText("Each row is a word or a translation", substring = true).assertExists()
		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertFalse(Dialogs.isShowing<InfoPopup>())
			assertTrue(Dialogs.isShowing<FlipConflictPopup>())
		}
	}
}
