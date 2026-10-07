package cz.litoj.schlmgr.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.io.Progress
import java.util.concurrent.atomic.AtomicInteger

/**
 * A progress-bar notification for one long file operation (an import or an export).
 * The I/O layer reports the bytes that have flowed through its counting stream
 * wrappers; the bar shows them against the [total] known beforehand.
 *
 * The channel is silent (low importance, no alert) and the notification is
 * ongoing, so it cannot be swiped away mid-operation. Updates are throttled to
 * [STEP_MS]: a notification per read chunk would flood the manager. A total of 0
 * (an unknown size) shows the indeterminate bar.
 *
 * Needs the `POST_NOTIFICATIONS` runtime permission on Android 13+ — without it
 * the calls are silently dropped and the import or export runs without a bar.
 */
class FileProgress(private val title: String, private val total: Long) : Progress, AutoCloseable {

	private val context = GlobalDependencies.appContext
	private val manager = context.getSystemService(NotificationManager::class.java)
	private val id = nextId.getAndIncrement()
	private var done = 0L
	private var lastPost = 0L

	init {
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL, context.getString(R.string.progress_channel),
				NotificationManager.IMPORTANCE_LOW,
			)
		)
		lastPost = SystemClock.elapsedRealtime()
		manager.notify(id, build())
	}

	/** Reports [bytes] more processed; the bar follows, throttled. */
	override fun advance(bytes: Int) {
		done += bytes
		val now = SystemClock.elapsedRealtime()
		if (now - lastPost < STEP_MS && done < total) return
		lastPost = now
		manager.notify(id, build())
	}

	override fun close() {
		manager.cancel(id)
	}

	private fun build(): Notification {
		val max = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
		return Notification.Builder(context, CHANNEL)
			.setSmallIcon(R.drawable.ic_app_mini)
			.setContentTitle(title)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(max, done.coerceAtMost(max.toLong()).toInt(), total <= 0)
			.build()
	}

	companion object {
		private const val CHANNEL = "file_progress"
		private const val STEP_MS = 100L
		private val nextId = AtomicInteger(1)
	}
}
