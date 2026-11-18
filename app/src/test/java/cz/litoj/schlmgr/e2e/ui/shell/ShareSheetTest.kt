package cz.litoj.schlmgr.e2e.ui.shell

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.ui.shell.MessageSnackbarHost
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - a share the user asked for opens its sheet at once, no extra tap in front of it.
 * - the share of a stored export still waits for its snackbar action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareSheetTest {

	@get:Rule
	val rule = createAndroidComposeRule<ComponentActivity>()

	private val file = Uri.parse("content://cz.litoj.schlmgr.fileprovider/shared/Kapitel.txt")

	private fun host() {
		rule.setContent { MessageSnackbarHost(messageSnackbarHost()) }
	}

	private fun emit(message: GlobalDependencies.UiMessage) {
		rule.runOnIdle { GlobalDependencies.messages.tryEmit(message) }
		rule.waitForIdle()
	}

	@Test
	fun `a share the user asked for opens the sheet without a tap`() {
		host()

		emit(GlobalDependencies.UiMessage.SendFile(file, "text/plain"))

		val chooser = shadowOf(rule.activity).nextStartedActivity!!
		val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
		assertEquals(Intent.ACTION_CHOOSER, chooser.action)
		assertEquals(Intent.ACTION_SEND, send.action)
		assertEquals(file, send.getParcelableExtra(Intent.EXTRA_STREAM))
		assertEquals("text/plain", send.type)
		assertTrue("the receiver gets no read access",
			send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
	}

	@Test
	fun `the share of a stored export waits for its snackbar action`() {
		host()

		emit(GlobalDependencies.UiMessage.ShareFile("Exported 3 words.", file, "text/plain"))

		rule.onNodeWithText("Exported 3 words.").assertExists()
		assertEquals(null, shadowOf(rule.activity).nextStartedActivity)
	}
}
