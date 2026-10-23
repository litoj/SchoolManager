package cz.litoj.schlmgr.ui.popup

import cz.litoj.schlmgr.app.AppSettings

/**
 * The crash of the previous session.
 * - the message up to the first blank line forms the body.
 * - the Copy button takes the whole report, and Dismiss consumes it.
 */
class CrashReportPopup(report: String) :
	MessagePopup(report.substringBefore("\n\n"), report) {

	override fun onDismissed() {
		AppSettings.remove("uncaughtException")
	}
}
