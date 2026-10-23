package cz.litoj.schlmgr.integration.app

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
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
 * - it appears only after the operation ran the show delay.
 * - a quick operation never flashes a notification.
 * - a quiet stretch past the delay still gets a bar from the pending post.
 * - the throttled advances keep it one notification.
 * - closing removes it.
 * - the stream proxies re-target the bar to the saved or walked rows.
 * - they count those rows through the boundary hooks.
 * - every test drives the injectable clock across the show delay.
 * - the lifetime of the bar belongs to the clock, not to the wall time.
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

	/** The latest posted notification: every test drives one bar only. */
	private fun bar(): Notification = shadowOf(manager).allNotifications.single()

	private fun barShownAfterDelay(): FileProgress =
		FileProgress("test") { now }.also { now = 5_000 }

	@Test
	fun `the bar appears only past the show delay of runtime and closes`() {
		FileProgress("test") { now }.use { progress ->
			progress.bytes(1000)
			progress.advance(400)
			// under the show delay, so nothing posts however the phase moves
			assertEquals(0, shadowOf(manager).allNotifications.size)
			now = 200
			progress.advance(100)
			assertEquals(1, shadowOf(manager).allNotifications.size)
			// a small move stays throttled, so only a fill step or the time step posts
			progress.advance(5)
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
		// the close removed the pending self-post, so nothing fires late
		shadowOf(Looper.getMainLooper()).runToEndOfTasks()
		assertEquals(0, shadowOf(manager).allNotifications.size)
	}

	@Test
	fun `a quiet stretch past the delay still shows its bar`() {
		FileProgress("test") { now }.use { progress ->
			progress.bytes(100)
			// every caller lands under the delay, so none posts
			progress.advance(10)
			assertEquals(0, shadowOf(manager).allNotifications.size)

			// the delay passes with no further call, so the pending self-post carries the bar
			now = 5_000
			shadowOf(Looper.getMainLooper()).runToEndOfTasks()
			assertEquals(1, shadowOf(manager).allNotifications.size)
		}
		assertEquals(0, shadowOf(manager).allNotifications.size)
	}

	@Test
	fun `the importer proxy re-targets the bar to the parsed rows and counts them`() {
		barShownAfterDelay().use { progress ->
			val item = StreamItem(DbItem(name = "w", description = "", type = ItemKind.WORD), null)
			val proxy = progress.importer(FakeImporter(List(3) { item }))
			// the re-target restarts the bar over the size of the stream
			assertEquals("0 / 3", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS_MAX))
			// every row is a third of the bar, a fill step each, posted on its own
			proxy.onItem(item)
			assertEquals(1, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			proxy.onItem(item)
			assertEquals(2, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			// the completion post skips the throttle too, so the final state shows
			proxy.onItem(item)
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			assertEquals("3 / 3", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
		}
	}

	@Test
	fun `the exporter proxy targets the bar at the walked stream and counts it`() {
		barShownAfterDelay().use { progress ->
			val item = StreamItem(DbItem(name = "w", description = "", type = ItemKind.WORD), null)
			val proxy = progress.exporter(FakeExporter())
			// before the stream hands over, the count is unknown, so the spinner
			assertTrue(bar().extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
			assertEquals("0 items", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
			// the handover re-targets the bar to the object count and fills it
			proxy.onStream(List(3) { item })
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS_MAX))
			assertEquals(3, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			assertEquals("3 / 3", bar().extras.getCharSequence(Notification.EXTRA_TEXT))
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
			// an unknown row total, the indeterminate bar with the count text
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

	@Test
	fun `a fast phase sweeps the bar with its real progress`() {
		barShownAfterDelay().use { progress ->
			progress.bytes(1000)
			// within the time step and below the fill step, so nothing posts
			progress.advance(20)
			assertEquals(0, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			// the worth of a fill step posts on its own, so a phase shorter than one
			// time step still sweeps to its real state
			progress.advance(40)
			assertEquals(60, bar().extras.getInt(Notification.EXTRA_PROGRESS))
			// a small move after it stays throttled within the time step
			progress.advance(5)
			assertEquals(60, bar().extras.getInt(Notification.EXTRA_PROGRESS))
		}
	}

	private class FakeImporter(override val items: List<StreamItem>) : DataImporter {
		override val references: List<StreamReference> = emptyList()
	}

	private class FakeExporter : DataExporter {
		override fun render(items: List<StreamItem>) = ""
	}
}
