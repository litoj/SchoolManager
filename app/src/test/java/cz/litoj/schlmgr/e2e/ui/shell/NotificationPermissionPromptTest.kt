package cz.litoj.schlmgr.e2e.ui.shell

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.shell.NOTIFICATION_PROMPT_SEEN
import cz.litoj.schlmgr.ui.shell.NotificationPermissionPrompt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - the first launch explains all long operations before Android asks.
 * - Not now leaves no system request.
 * - an already granted permission skips the explanation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPermissionPromptTest {

	@get:Rule
	val rule = createAndroidComposeRule<ComponentActivity>()

	private val app get() = RuntimeEnvironment.getApplication()
	private lateinit var settings: Settings

	@Before
	fun setUp() {
		settings = Settings(app)
		settings.remove(NOTIFICATION_PROMPT_SEEN)
		shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		settings.remove(NOTIFICATION_PROMPT_SEEN)
	}

	private fun showPrompt() {
		rule.setContent {
			Box(Modifier.fillMaxSize()) {
				DialogHost()
				NotificationPermissionPrompt(settings)
			}
		}
		rule.waitForIdle()
	}

	private fun label(res: Int): String = app.getString(res)

	@Test
	fun `the first launch explains the progress notifications before asking`() {
		showPrompt()

		rule.onNodeWithText(label(R.string.notification_permission_title)).assertIsDisplayed()
		rule.onNodeWithText(label(R.string.notification_permission_body)).assertIsDisplayed()
		rule.runOnIdle {
			assertTrue(settings.getBool(NOTIFICATION_PROMPT_SEEN, false))
			assertNull("the system is asked only after the explanation",
				shadowOf(rule.activity).lastRequestedPermission)
		}
	}

	@Test
	fun `Allow opens the system permission request`() {
		showPrompt()

		rule.onNodeWithText(label(R.string.notification_permission_allow)).performClick()
		rule.waitForIdle()

		rule.runOnIdle {
			val request = shadowOf(rule.activity).lastRequestedPermission
			assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS),
				request?.requestedPermissions?.toList())
		}
	}

	@Test
	fun `Not now closes without a system request`() {
		showPrompt()

		rule.onNodeWithText(label(R.string.notification_permission_later)).performClick()
		rule.waitForIdle()

		assertTrue(rule.onAllNodesWithText(label(R.string.notification_permission_title))
			.fetchSemanticsNodes().isEmpty())
		rule.runOnIdle { assertNull(shadowOf(rule.activity).lastRequestedPermission) }
	}

	@Test
	fun `an already granted permission skips the explanation`() {
		shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
		showPrompt()

		assertTrue(rule.onAllNodesWithText(label(R.string.notification_permission_title))
			.fetchSemanticsNodes().isEmpty())
		rule.runOnIdle {
			assertEquals(false, settings.getBool(NOTIFICATION_PROMPT_SEEN, false))
			assertNull(shadowOf(rule.activity).lastRequestedPermission)
		}
	}
}
