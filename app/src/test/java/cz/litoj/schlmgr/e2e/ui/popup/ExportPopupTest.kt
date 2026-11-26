package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.ExportPopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the sheet asks once: the history and its destination.
 * - both of its doors take the format the switch shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportPopupTest {

	@get:Rule
	val rule = createComposeRule()

	private val saved = mutableListOf<ExportFormat>()
	private val shared = mutableListOf<ExportFormat>()
	private lateinit var settings: Settings

	@Before
	fun setUp() {
		settings = Settings(RuntimeEnvironment.getApplication())
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun openSheet(): ExportPopup {
		val popup = ExportPopup(settings, saved::add, shared::add)
		rule.setContent { popup.Content() }
		return popup
	}

	@Test
	fun `the sheet names its history switch and offers both destinations`() {
		openSheet()

		rule.onNodeWithText("Omit testing history").assertIsDisplayed()
		rule.onNodeWithText("Share").assertIsDisplayed()
		rule.onNodeWithText("Save to file").assertIsDisplayed()
	}

	@Test
	fun `the button at the right edge is the one that shares`() {
		openSheet()
		val share = rule.onNodeWithText("Share").fetchSemanticsNode().boundsInRoot.center.x
		val save = rule.onNodeWithText("Save to file").fetchSemanticsNode().boundsInRoot.center.x

		assertTrue("share sits on the right, where every popup waits for its action", share > save)
	}

	@Test
	fun `the switch explains the file it writes`() {
		openSheet()

		rule.onNodeWithText("Writes a word file without success rates or references.")
			.assertIsDisplayed()
		rule.onNodeWithText("Omit testing history").performClick()
		rule.onNodeWithText("Writes a JSON file with success rates and references.")
			.assertIsDisplayed()
	}

	@Test
	fun `a door takes the format the switch shows and closes the sheet`() {
		openSheet()
		rule.onNodeWithText("Omit testing history").performClick()

		rule.onNodeWithText("Share").performClick()

		rule.runOnIdle {
			assertEquals(listOf(ExportFormat.JSON), shared)
			assertFalse(Dialogs.isShowing<ExportPopup>())
		}
	}

	@Test
	fun `a door stores its format as the next opening one`() {
		openSheet()

		rule.onNodeWithText("Save to file").performClick()

		rule.runOnIdle {
			assertEquals(listOf(ExportFormat.WORD), saved)
			assertEquals(
				ExportFormat.WORD.name,
				settings.getString("exportFormat", ExportFormat.JSON.name),
			)
		}
	}

	@Test
	fun `the sheet opens again on the format of the last export`() {
		val first = openSheet()
		first.toggleFormat()
		first.pickSave()

		// the next sheet takes its format from the store, so a repeat needs no toggle
		rule.runOnIdle {
			assertEquals(ExportFormat.JSON, ExportPopup(settings, saved::add, shared::add).format)
		}
	}
}
