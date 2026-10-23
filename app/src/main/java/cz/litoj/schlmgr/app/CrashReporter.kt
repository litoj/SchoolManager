package cz.litoj.schlmgr.app

import android.annotation.SuppressLint
import android.util.Log
import cz.litoj.schlmgr.R

/** The process-wide crash sink, and the stored report the next launch reads. */
object CrashReporter {

	/**
	 * Installs the process-wide crash sink without calling the platform handler.
	 * - [onUncaught] owns the report and the exit.
	 * - delegating would double-report and double-kill.
	 */
	@SuppressLint("DefaultUncaughtExceptionDelegation")
	fun install() {
		Thread.setDefaultUncaughtExceptionHandler { t, e -> onUncaught(t, e) }
	}

	private fun isDebugBuild(): Boolean =
		(GlobalDependencies.appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

	/**
	 * The hard cap of the stored report.
	 * - a StackOverflowError fills tens of thousands of frames.
	 * - the multi-megabyte report then breaks every binder transfer, such as the popup copy.
	 * - a few hundred KB keeps the report readable and under the 1 MB limit.
	 */
	private const val MAX_REPORT_CHARS = 256 * 1024

	/**
	 * The process-wide uncaught-exception sink.
	 * - it must never throw.
	 * - it must persist synchronously, so the next launch finds the report.
	 * - commit rather than apply is the only write that lands before the process dies.
	 */
	private fun onUncaught(t: Thread, e: Throwable) {
		try {
			// the trace head is where the error started, so the cap cuts only the tail.
			// building the trace can itself fail, but the message alone is still a report
			val fullMsg = runCatching { boundReport("$t\n${Log.getStackTraceString(e)}") }
				.getOrElse { "${e.toString()}\n[the trace was lost: ${it.message}]" }
			val report = GlobalDependencies.appContext.getString(R.string.exception_handler) +
				(Throwables.getFirstCause(e) ?: e.toString()) + "\n\n$fullMsg"
			AppSettings.setNow("uncaughtException", report)
			if (isDebugBuild()) Log.e("Unexpected failure", fullMsg)
		} catch (_: Throwable) {
			// the handler itself must stay inert, and the kill below still ends the process
		} finally {
			// killProcess rather than exit alone, so the activity record cannot
			// stay half-launched when the process dies
			android.os.Process.killProcess(android.os.Process.myPid())
			System.exit(10)
		}
	}

	@JvmStatic
	fun boundReport(msg: String): String =
		if (msg.length <= MAX_REPORT_CHARS) msg
		else msg.substring(0, MAX_REPORT_CHARS) + "\n[truncated]"

	/** Stored crash report from the previous run, or null when there is none. */
	@JvmStatic
	fun getCrashReport(): String? =
		AppSettings.getString("uncaughtException")
}
