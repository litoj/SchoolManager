package cz.litoj.schlmgr.ui.popup

import cz.litoj.schlmgr.app.AppSettings

/**
 * Shows the message of an unexpected failure from a previous session through
 * [MessagePopup]: the body carries the message alone, the report's stack trace
 * is not shown — the Copy action copies the whole stored report instead. The
 * message part ends at the report's first blank line, the separator the crash
 * handler puts between the message and the thread/stack-trace part.
 *
 * Dismissing it (in any way) consumes the stored report so it is not shown
 * again.
 */
class CrashReportPopup(report: String) :
	MessagePopup(report.substringBefore("\n\n"), report) {

	override fun onDismissed() {
		AppSettings.remove("uncaughtException")
	}
}
