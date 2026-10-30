package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.ui.component.BareTextField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the loss callback fires on leaving and never on entering.
 * - the wrapper is only the ancestor of the focus holder.
 * - its isFocused stays false while hasFocus carries the truth.
 * - typing relays to the value, an external change re-seeds the field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BareTextFieldFocusTest {

	@get:Rule
	val rule = createComposeRule()

	@Test
	fun `the loss callback fires on leaving, not on entering`() {
		var lost = 0
		rule.setContent {
			Column {
				BareTextField(
					value = "first", onValueChange = {}, fontSize = 15.sp,
					onFocusLost = { lost++ },
				)
				BareTextField(value = "second", onValueChange = {}, fontSize = 15.sp)
			}
		}

		// the focus traversal of the window touches the first field during setup,
		// so let it settle and count from zero
		rule.waitForIdle()
		lost = 0

		// tapping in must not fire the loss callback
		rule.onNodeWithText("first").performClick()
		rule.runOnIdle { assertEquals(0, lost) }

		rule.onNodeWithText("second").performClick()
		rule.runOnIdle { assertEquals(1, lost) }
	}

	@Test
	fun `typing relays and an external value change re-seeds the field`() {
		var value by mutableStateOf("first")
		rule.setContent { BareTextField(value, { value = it }, fontSize = 15.sp) }
		rule.waitForIdle()

		rule.onNodeWithText("first").performClick()
		rule.onNodeWithText("first").performTextInput("x")
		rule.runOnIdle { assertEquals("firstx", value) }

		value = "second"
		rule.waitForIdle()
		rule.onNodeWithText("second").assertExists()
	}
}
