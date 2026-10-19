package cz.litoj.schlmgr.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.StreamItem
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reports the amount that has flowed through one long file operation —
 * the bridge from [FileText]'s counting streams to the implementations
 * that drive a bar.
 */
fun interface Progress {
	/** Reports more processed — bytes in the file phase, rows in the row phase. */
	fun advance(amount: Int)
}

/**
 * A progress-bar notification for one long file operation, one counted
 * phase at a time. Imports count bytes then rows, exports rows then
 * written bytes, deletions count rows.
	 * - the bar always appears after [SHOW_DELAY_MS] of activity — a pending self-post carries it over a quiet stretch.
	 * - a call before the delay never flashes one for a finished operation.
	 * - without `POST_NOTIFICATIONS` (Android 13+) posts are dropped.
	 * - the run goes on without a bar.
 *
 * @param clock elapsed-ms source, injectable so tests can drive the throttle
 */
class FileProgress @JvmOverloads constructor(
	private val title: String,
	private val clock: () -> Long = SystemClock::elapsedRealtime,
) : Progress, AutoCloseable {

	private val context = GlobalDependencies.appContext
	private val manager = context.getSystemService(NotificationManager::class.java)
	private val id = nextId.getAndIncrement()
	/**
	 * The current counted phase — rows show the "done / max" count as text,
	 * and an unknown row count keeps the indeterminate bar with the count
	 * standing alone.
	 */
	private sealed interface Phase {
		fun isComplete(done: Long): Boolean
		/** Bar maximum, clamped to int. */
		fun max(): Int
		/** Count text, null when the phase moves only the bar. */
		fun text(app: Context, done: Long): CharSequence?
		/** Whether the bar runs indeterminate — an unknown or empty total. */
		fun indeterminate(): Boolean
		data class Bytes(val total: Long) : Phase {
			override fun isComplete(done: Long) = total > 0 && done >= total
			override fun max() = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
			// kilobytes as text would be noise — the phase moves only the bar
			override fun text(app: Context, done: Long) = null
			override fun indeterminate() = total <= 0
		}
		data class RowsKnown(val total: Int) : Phase {
			override fun isComplete(done: Long) = done >= total
			override fun max() = total
			override fun text(app: Context, done: Long) =
				app.getString(R.string.progress_count, done.toInt(), max())
			override fun indeterminate() = false
		}
		data object RowsUnknown : Phase {
			override fun isComplete(done: Long) = false
			override fun max() = 0
			override fun text(app: Context, done: Long): CharSequence? =
				app.resources.getQuantityString(R.plurals.progress_items, done.toInt(), done.toInt())
			override fun indeterminate() = true
		}
	}
	private var phase: Phase = Phase.Bytes(0)
	private var done = 0L
	private val throttle = ThrottleGate()
	private val start = clock()
	/** Whether the bar passed the show delay — later posts skip it. */
	private var shown = false
	/** Carries the bar over a quiet stretch — see [init]. */
	private val show = Handler(Looper.getMainLooper())
	/**
	 * Drops posts that arrive within the step — one notification per read
	 * chunk would flood the manager.
	 */
	private inner class ThrottleGate {
		private var lastPost = 0L
		/** Whether a post passes — completion always does, otherwise only past [STEP_MS]. */
		fun gate(complete: Boolean): Boolean {
			if (complete) {
				posted()
				return true
			}
			if (clock() - lastPost >= STEP_MS) {
				posted()
				return true
			}
			return false
		}
		fun posted() {
			lastPost = clock()
		}
	}

	init {
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL, context.getString(R.string.progress_channel),
				NotificationManager.IMPORTANCE_LOW,
			)
		)
		// the bar appears once the operation ran the delay even when no
		// phase step calls in — a quiet stretch must not hide it. Close
		// removes the pending post, a finished operation never flashes one.
		show.postDelayed({ post() }, SHOW_DELAY_MS)
	}

	/** Targets the bar at the read or write phase — total bytes, 0 or below when unknown. */
	fun bytes(total: Long) {
		phase = Phase.Bytes(total)
		done = 0
		post()
	}

	/** Wraps [importer] to report each taken entry, re-targeted to the stream size. */
	fun importer(importer: DataImporter): DataImporter {
		rows(importer.items.size)
		return object : DataImporter by importer {
			override fun onItem(entry: StreamItem) {
				advance(1)
				importer.onItem(entry)
			}
		}
	}

	/**
	 * Wraps [exporter] to report each walked row. The count is unknown until
	 * [bytes] re-targets the bar for the write.
	 */
	fun exporter(exporter: DataExporter): DataExporter {
		rows(0)
		return object : DataExporter by exporter {
			override fun onItem(item: StreamItem) {
				advance(1)
				exporter.onItem(item)
			}
		}
	}

	/** Re-targets the bar at [total] rows, restarted at zero — 0 or below means an unknown count. */
	fun rows(total: Int) {
		phase = if (total <= 0) Phase.RowsUnknown else Phase.RowsKnown(total)
		done = 0
		post()
	}

	/** Reports more processed — the bar follows, throttled. */
	override fun advance(amount: Int) {
		done += amount
		if (!shown && clock() - start < SHOW_DELAY_MS) return
		shown = true
		if (!throttle.gate(complete())) return
		manager.notify(id, build())
	}

	override fun close() {
		show.removeCallbacksAndMessages(null)
		manager.cancel(id)
	}

	private fun post() {
		if (!shown) {
			if (clock() - start < SHOW_DELAY_MS) return
			shown = true
		}
		throttle.posted()
		manager.notify(id, build())
	}

	/** Whether the phase has reached its end — an unknown total never completes. */
	private fun complete(): Boolean = phase.isComplete(done)

	private fun build(): Notification {
		val max = phase.max()
		return Notification.Builder(context, CHANNEL)
			.setSmallIcon(R.drawable.ic_app_mini)
			.setContentTitle(title)
			.setContentText(phase.text(context, done))
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(max, done.coerceAtMost(max.toLong()).toInt(), phase.indeterminate())
			.build()
	}

	companion object {
		private const val CHANNEL = "file_progress"
		private const val STEP_MS = 100L
		/** How long an operation must run before its bar appears at all. */
		private const val SHOW_DELAY_MS = 200L
		private val nextId = AtomicInteger(1)
	}
}
