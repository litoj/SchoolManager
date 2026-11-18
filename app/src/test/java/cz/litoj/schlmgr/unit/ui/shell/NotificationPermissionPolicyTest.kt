package cz.litoj.schlmgr.unit.ui.shell

import cz.litoj.schlmgr.ui.shell.shouldAskNotificationPermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - only Android 13 and later can ask for notifications.
 * - a grant or an earlier explanation ends the ask.
 */
class NotificationPermissionPolicyTest {

	@Test
	fun `the first ungranted start on Android 13 asks`() {
		assertTrue(shouldAskNotificationPermission(33, granted = false, asked = false))
	}

	@Test
	fun `older Android asks nothing`() {
		assertFalse(shouldAskNotificationPermission(32, granted = false, asked = false))
	}

	@Test
	fun `a granted permission asks nothing`() {
		assertFalse(shouldAskNotificationPermission(33, granted = true, asked = false))
	}

	@Test
	fun `an explained permission asks nothing again`() {
		assertFalse(shouldAskNotificationPermission(33, granted = false, asked = true))
	}
}
