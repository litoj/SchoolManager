package cz.litoj.schlmgr.ui.popup

import cz.litoj.schlmgr.app.AppSettings

/**
 * Previous session's crash — the message up to the first blank line
 * forms the body, Copy takes the whole report. Dismiss consumes it.
 */
class CrashReportPopup(report: String) :
	MessagePopup(report.substringBefore("\n\n"), report) {

	override fun onDismissed() {
		AppSettings.remove("uncaughtException")
	}
}
