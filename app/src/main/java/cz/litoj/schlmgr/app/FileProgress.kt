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
 * Reports the amount that has flowed through one long file operation.
 * It bridges the counting streams of [FileText] to the implementations that drive a bar.
 */
fun interface Progress {
	/** Reports more processed: bytes in the file phase, rows in the row phase. */
	fun advance(amount: Int)
}

/**
 * A progress-bar notification for one long file operation, one counted phase at a time.
 * - imports count bytes then rows.
 * - exports count the walked objects.
 * - deletions count rows.
 * - the bar appears after [SHOW_DELAY_MS] of activity.
 * - a call before the delay never flashes a bar for a finished operation.
 * - without POST_NOTIFICATIONS on Android 13 and later posts are dropped.
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
	 * The current counted phase.
	 * - rows show the "done / max" count as text.
	 * - an unknown row count keeps the indeterminate bar and shows the count alone.
	 */
	private sealed interface Phase {
		fun isComplete(done: Long): Boolean
		fun max(): Int
		/** Count text, null when the phase moves only the bar. */
		fun text(app: Context, done: Long): CharSequence?
		/** Whether the bar runs indeterminate, for an unknown or empty total. */
		fun indeterminate(): Boolean
		data class Bytes(val total: Long) : Phase {
			override fun isComplete(done: Long) = total > 0 && done >= total
			override fun max() = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
			// kilobytes as text would be noise, so the phase moves only the bar
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
	/** Whether the bar passed the show delay, so later posts skip it. */
	private var shown = false
	private val show = Handler(Looper.getMainLooper())
	/**
	 * Drops posts that arrive within the step.
	 * - one notification per read chunk would flood the manager.
	 * - a fill step passes on its own.
	 * - a short phase such as an export write starts and ends within one time step.
	 * - its bar must still sweep with the real progress.
	 * - a frozen stale value would only die with the completion post and the cancel.
	 */
	private inner class ThrottleGate {
		private var lastPost = 0L
		/** The fill of the bar at the last post, per-mille, negative without a fill. */
		private var lastFill = -1
		/** Whether a post passes, by completion, a fill step or the time step. */
		fun gate(complete: Boolean, fill: Int): Boolean {
			if (complete || fill - lastFill >= FILL_STEP || clock() - lastPost >= STEP_MS) {
				posted(fill)
				return true
			}
			return false
		}
		fun posted(fill: Int) {
			lastPost = clock()
			lastFill = fill
		}
	}

	init {
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL, context.getString(R.string.progress_channel),
				NotificationManager.IMPORTANCE_LOW,
			)
		)
		// the bar appears once the operation ran the delay even when no phase step calls in.
		// - a quiet stretch must not hide it.
		// - close removes the pending post, so a finished operation never flashes one.
		show.postDelayed({ post() }, SHOW_DELAY_MS)
	}

	/** Targets the bar at the read or write phase, total bytes, 0 or below when unknown. */
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
	 * Wraps [exporter] to count the written objects.
	 * - the stream handover re-targets the bar to the stream size.
	 * - each item counts as one written.
	 * - until then the walk keeps the unknown-count spinner.
	 * - the render and the file write report nothing, so they never pay for statistics.
	 */
	fun exporter(exporter: DataExporter): DataExporter {
		rows(0)
		return object : DataExporter by exporter {
			override fun onStream(items: List<StreamItem>) {
				rows(items.size)
				items.forEach { advance(1) }
				exporter.onStream(items)
			}
		}
	}

	/**
	 * Re-targets the bar at [total] rows, restarted at zero.
	 * A value of 0 or below means an unknown count.
	 */
	fun rows(total: Int) {
		phase = if (total <= 0) Phase.RowsUnknown else Phase.RowsKnown(total)
		done = 0
		post()
	}

	override fun advance(amount: Int) {
		done += amount
		if (!shown && clock() - start < SHOW_DELAY_MS) return
		shown = true
		if (!throttle.gate(complete(), fillPerMille())) return
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
		throttle.posted(fillPerMille())
		manager.notify(id, build())
	}

	private fun complete(): Boolean = phase.isComplete(done)

	private fun fillPerMille(): Int {
		if (phase.indeterminate()) return -1
		val max = phase.max()
		if (max <= 0) return -1
		return ((done.coerceAtMost(max.toLong()) * 1000) / max).toInt()
	}

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
		/** The fill step that posts on its own, per-mille of the phase maximum. */
		private const val FILL_STEP = 50
		/** How long an operation must run before its bar appears at all. */
		private const val SHOW_DELAY_MS = 200L
		private val nextId = AtomicInteger(1)
	}
}
