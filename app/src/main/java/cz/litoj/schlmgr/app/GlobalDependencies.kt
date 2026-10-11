package cz.litoj.schlmgr.app

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide dependencies (the app context, the message bus), owned by
 * [cz.litoj.schlmgr.app.Startup.init] — readers come here instead of the
 * shell activity.
 */
object GlobalDependencies {

	/**
	 * The application context — safe to hold for the whole process
	 * lifetime, also from a crashing thread.
	 */
	lateinit var appContext: Context

	/**
	 * The transient user messages — the reaction surface reports through
	 * [cz.litoj.schlmgr.app.Startup], the shell shows each as a snackbar.
	 * Emitters may come from worker threads, and `extraBufferCapacity`
	 * keeps reports non-blocking.
	 */
	val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

	/**
	 * One snackbar-level message — either a resolved text, or a notice
	 * by meaning. The reporters that know only what happened (the view
	 * models) leave every string to the UI.
	 */
	sealed interface UiMessage {
		/**
		 * A message with resolved text — [text] is the snackbar line and
		 * the popup body, [fullText] the Copy payload.
		 * - a null [actionLabel] uses the default label.
		 */
		data class Text(val text: String, val fullText: String, val actionLabel: String? = null) : UiMessage

		/** A notice the display resolves — see [UiNotice]. */
		data class Notice(val notice: UiNotice) : UiMessage
	}
}
