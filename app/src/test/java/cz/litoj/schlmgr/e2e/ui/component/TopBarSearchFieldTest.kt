package cz.litoj.schlmgr.e2e.ui.component

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cz.litoj.schlmgr.ui.component.TopBarSearchField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search field against the keyboard, driven through the window insets.
 * - a dismissed keyboard closes the field.
 * - the help keeps the field and its query, though its popup window hides the keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TopBarSearchFieldTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var view: View
	private var closes = 0
	private var helps = 0

	private fun show() {
		rule.setContent {
			view = LocalView.current
			TopBarSearchField("hled", {}, {}, { closes++ }, onHelp = { helps++ })
		}
		rule.waitForIdle()
		keyboard(visible = true)
	}

	private fun keyboard(visible: Boolean) {
		rule.runOnIdle {
			val ime = WindowInsetsCompat.Type.ime()
			ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
				.setInsets(ime, Insets.of(0, 0, 0, if (visible) 300 else 0))
				.setVisible(ime, visible)
				.build())
		}
		rule.waitForIdle()
	}

	@Test
	fun `a dismissed keyboard closes the field`() {
		show()

		keyboard(visible = false)

		rule.runOnIdle { assertEquals(1, closes) }
	}

	@Test
	fun `the help keeps the field and the query when the keyboard hides`() {
		show()

		rule.onNodeWithContentDescription("Help").performClick()
		keyboard(visible = false)

		rule.runOnIdle {
			assertEquals(1, helps)
			assertEquals(0, closes)
		}
		rule.onNodeWithText("hled").assertExists()
	}
}
