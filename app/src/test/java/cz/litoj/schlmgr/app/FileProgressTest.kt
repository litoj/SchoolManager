package cz.litoj.schlmgr.app

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The progress-bar notification of the file imports and exports: it posts with a
 * bar (the very first post shows, so a short operation is not missed), the
 * throttled advances keep it one notification, and closing it removes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileProgressTest {

	private lateinit var manager: NotificationManager

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		manager = GlobalDependencies.appContext.getSystemService(NotificationManager::class.java)!!
	}

	@Test
	fun `the bar posts, follows the bytes and closes`() {
		FileProgress("test", 100).use { progress ->
			assertEquals(1, shadowOf(manager).allNotifications.size)
			progress.advance(40)
			// the throttle keeps it a single notification until the step passes
			assertEquals(1, shadowOf(manager).allNotifications.size)
		}
		assertEquals(0, shadowOf(manager).allNotifications.size)
	}
}
