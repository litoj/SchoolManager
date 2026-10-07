package cz.litoj.schlmgr.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The app's hand-rolled dependency container — the process-wide
 * dependencies in one place, initialized once from
 * [cz.litoj.schlmgr.app.Startup.init] before any other code runs. It
 * replaces the context/message statics the former `Controller` held:
 * layers that need the application context or the message bus read them
 * here instead of reaching into the shell activity.
 */
object GlobalDependencies {

	/**
	 * The application context — safe to hold for the whole process
	 * lifetime, also from a crashing thread.
	 */
	lateinit var appContext: Context

	/**
	 * The transient user messages: the former reaction surface reports
	 * through [cz.litoj.schlmgr.app.Startup], the activity shell collects
	 * the flow and shows each message as a snackbar on the main thread.
	 * Emitters may come from worker threads; `extraBufferCapacity` keeps
	 * the reports non-blocking.
	 */
	val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

	/** One snackbar-level message with the full text behind its action. */
	data class UiMessage(val text: String, val fullText: String)
}
