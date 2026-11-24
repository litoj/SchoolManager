package cz.litoj.schlmgr.e2e.ui.activity

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.activity.SettingsActivity
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.MessagePopup
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsActivityDialogTest {

	@get:Rule
	val rule = createAndroidComposeRule<SettingsActivity>()

	@After
	fun tearDown() {
		Dialogs.clear()
		ItemRepository.resetForTest()
	}

	@Before
	fun setUp() {
		Dialogs.clear()
	}

	@Test
	fun `the full text snackbar action renders its dialog here`() {
		rule.runOnIdle {
			GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Text("short", "full details"))
		}
		rule.waitForIdle()

		rule.onNodeWithText("Full Text").performClick()
		rule.waitForIdle()
		val shown = Dialogs.isShowing<MessagePopup>()
		assertTrue("message popup is on the dialog stack", shown)
		assertTrue("the host opened its dialog window", ShadowDialog.getLatestDialog().isShowing)
	}
}
