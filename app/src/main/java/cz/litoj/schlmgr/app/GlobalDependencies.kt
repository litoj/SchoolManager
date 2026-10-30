package cz.litoj.schlmgr.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide dependencies: the app context and the message bus.
 * - [cz.litoj.schlmgr.app.Startup.init] owns them.
 * - readers come here instead of the shell activity.
 */
object GlobalDependencies {

	/** The application context, safe to hold for the whole process, also from a crashing thread. */
	lateinit var appContext: Context

	/**
	 * The transient user messages.
	 * - the reaction surface reports through [cz.litoj.schlmgr.app.UserMessages].
	 * - the shell shows each as a snackbar.
	 * - emitters may come from worker threads.
	 * - extraBufferCapacity keeps reports non-blocking.
	 */
	val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

	/**
	 * The reporters that know only what happened, the view models, leave every string to the UI.
	 */
	sealed interface UiMessage {
		/**
		 * A message with resolved text.
		 * - [text] is the snackbar line and the popup body.
		 * - [fullText] is the Copy payload.
		 * - a null [actionLabel] uses the default label.
		 */
		data class Text(val text: String, val fullText: String, val actionLabel: String? = null) : UiMessage

		/** A notice that the display resolves, see [UiNotice]. */
		data class Notice(val notice: UiNotice) : UiMessage

		/** An export result with a share action on the saved document. */
		data class ShareFile(val text: String, val uri: Uri, val mime: String) : UiMessage
	}
}
