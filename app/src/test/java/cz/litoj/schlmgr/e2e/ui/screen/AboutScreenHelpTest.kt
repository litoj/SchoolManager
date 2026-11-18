package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.screen.about.AboutScreen
import cz.litoj.schlmgr.ui.screen.about.AboutViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The help tree as a searchable list of collapsible rows.
 * - a row shows its content only while open, its chevron marks the state.
 * - the search matches the code example of a section too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutScreenHelpTest {

	@get:Rule
	val rule = createComposeRule()

	private fun text(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	private fun screen(viewModel: AboutViewModel) {
		rule.setContent { AboutScreen(viewModel, {}) }
		rule.waitForIdle()
	}

	@Test
	fun `a header tap shows its content and a second tap drops it`() {
		val model = AboutViewModel()
		screen(model)
		val title = text(R.string.help_select)
		val content = text(R.string.help_select_how).lineSequence().first()

		rule.onNodeWithText(title).performClick()
		rule.waitForIdle()
		rule.onNodeWithText(content, substring = true).assertExists()

		rule.onNodeWithText(title).performClick()
		rule.waitForIdle()
		rule.onNodeWithText(content, substring = true).assertDoesNotExist()
	}

	@Test
	fun `the search reads the example block of a section`() {
		val model = AboutViewModel()
		screen(model)

		rule.runOnIdle {
			model.openSearchField()
			model.onQueryChange("(I go)")
		}
		rule.waitForIdle()

		// only the naming section and its ancestors answer that pattern
		rule.onNodeWithText(text(R.string.help_extra_naming)).assertExists()
		rule.onNodeWithText(text(R.string.help_create)).assertDoesNotExist()
	}

}
