package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.ReferencePopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the ok button commits the kept ids once and closes the popup itself.
 * - the teardown clears the shared stack, so no dialog leaks across tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReferencePopupOkTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `an ok commits the kept ids once and closes the popup`() {
		val popup = ReferencePopup(listOf(
			DbItem(id = 1, name = "pes", description = "", type = ItemKind.WORD),
			DbItem(id = 2, name = "dog", description = "", type = ItemKind.WORD)))
		val commits = ArrayList<List<Int>>()
		popup.setOkListener { commits.add(it) }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()
		// a double tap must not commit twice, the listener is one shot
		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertEquals(listOf(listOf(1, 2)), commits)
			assertFalse(Dialogs.isShowing<ReferencePopup>())
		}
	}
}
