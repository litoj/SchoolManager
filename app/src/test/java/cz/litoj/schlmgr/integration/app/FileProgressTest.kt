package cz.litoj.schlmgr.integration.app

import android.app.Notification
import android.app.NotificationManager
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The progress-bar notification of the file imports and exports.
 * - it appears only once the operation ran half a second — a quick one never
 *   flashes a notification.
 * - the throttled advances keep it one notification and closing removes it.
 * - the stream proxies of the middleware re-target the bar to the saved or
 *   walked rows and count them through the boundary hooks.
 * - every test drives the injectable clock across the show delay
 *   explicitly — the bar's lifetime is the clock's, not the wall's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileProgressTest {

	private lateinit var manager: NotificationManager
	private var now = 0L

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		manager = GlobalDependencies.appContext.getSystemService(NotificationManager::class.java)!!
	}

	/** The latest posted notification — every test drives one bar only. */
	private fun bar(): Notification = shadowOf(manager).allNotifications.single()

	/** A bar over the injectable clock, already past the show delay. */
	private fun barShownAfterDelay(): FileProgress =
		FileProgress("test") { now }.also { now = 5_000 }

	@Test
	fun `the bar appears only after half a second of runtime and closes`() {
		FileProgress("test") { now }.use { progress ->
			progress.bytes(100)
			progress.advance(40)
			// under the show delay — nothing posted, however the phase moves
			assertEquals(0, shadowOf(manager).allNotifications.size)
			now = 500
			progress.advance(10)
			assertEquals(1, shadowOf(manager).allNotifications.size)
			// the throttle keeps it a single notification until the step passes
			progress.advance(10)
			assertEquals(1, shadowOf(manager).allNotifications.size)
		}
		assertEquals(0, shadowOf(manager).allNotifications.size)
	}

	@Test
	fun `an operation finishing under the delay never shows a bar`() {
		FileProgress("test") { now }.use { progress ->
			progress.bytes(100)
			progress.advance(100) // the completion post, still under the delay
			assertEquals(0, shadowOf(manager).allNotifications.size)
		}
		assertEquals(0, shadowOf(manager).allNotifications.size)
	}

	@Test
	fun `the importer proxy re-targets the bar to the parsed rows and counts them`() {
		barShownAfterDelay().use { progress ->
			val item = StreamItem(DbItem(name = "w", description = "", type = ItemKind.WORD), null)
			val proxy = progress.importer(FakeImporter(List(3) { item }))
			// the re-target restarts the bar over the stream's size
			assertEquals("0 / 3", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS_MAX))
			// mid-stream advances stay throttled away — the shown count lags
			proxy.onItem(item)
			proxy.onItem(item)
			assertEquals(0, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			// the completion post skips the throttle — the final state shows
			proxy.onItem(item)
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			assertEquals("3 / 3", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	@Test
	fun `the exporter proxy counts walked rows on the indeterminate bar`() {
		barShownAfterDelay().use { progress ->
			progress.exporter(FakeExporter())
			assertTrue(bar().extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
			assertEquals("0 items", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	@Test
	fun `a byte phase re-targets the bar and drops the count text`() {
		barShownAfterDelay().use { progress ->
			val item = StreamItem(DbItem(name = "w", description = "", type = ItemKind.WORD), null)
			progress.importer(FakeImporter(listOf(item)))
			progress.bytes(50)
			assertEquals(50, bar().extras.getInt(Notification.EXTRA_PROGRESS_MAX))
			assertEquals(null, bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	@Test
	fun `the rows phase targets the bar for the deletion`() {
		barShownAfterDelay().use { progress ->
			// an unknown row total — the indeterminate bar with the count text
			progress.rows(0)
			assertTrue(bar().extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
			assertEquals("0 items", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
			now += 100
			progress.advance(1)
			now += 100
			progress.advance(1)
			assertEquals("2 items", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	@Test
	fun `a known rows total shows the processed count over the bar maximum`() {
		barShownAfterDelay().use { progress ->
			progress.rows(4)
			assertEquals(4, bar().extras.getInt(Notification.EXTRA_PROGRESS_MAX))
			assertEquals("0 / 4", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
			now += 100
			progress.advance(2)
			assertEquals(2, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			assertEquals("2 / 4", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	private class FakeImporter(override val items: List<StreamItem>) : DataImporter {
		override val references: List<StreamReference> = emptyList()
	}

	private class FakeExporter : DataExporter {
		override fun render(items: List<StreamItem>) = ""
	}
}
