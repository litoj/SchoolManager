package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - cancel keeps the action unfired, ok runs it once, both close the popup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContinuePopupTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `cancel dismisses without firing the action`() {
		var fired = 0
		val popup = ContinuePopup { fired++ }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Cancel").performClick()

		rule.runOnIdle {
			assertEquals(0, fired)
			assertFalse(Dialogs.isShowing<ContinuePopup>())
		}
	}

	@Test
	fun `ok fires the action once and dismisses`() {
		var fired = 0
		val popup = ContinuePopup { fired++ }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertEquals(1, fired)
			assertFalse(Dialogs.isShowing<ContinuePopup>())
		}
	}
}
