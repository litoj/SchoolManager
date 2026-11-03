package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The setting rows treat their label as the way into the control.
 * - a label tap focuses the field, like a tap on the field itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingRowsTest {

	@get:Rule
	val rule = createComposeRule()

	@Test
	fun `a label tap focuses its field`() {
		rule.setContent { SettingNumberRow("Amount", "10", {}, {}) }

		rule.onNodeWithText("Amount").performClick()
		rule.waitForIdle()

		rule.onNode(hasSetTextAction()).assertIsFocused()
	}
}
