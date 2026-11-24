package cz.litoj.schlmgr.ui.popup

import cz.litoj.schlmgr.app.Settings

/**
 * The crash of the previous session.
 * - the message up to the first blank line forms the body.
 * - the Copy button takes the whole report, and Dismiss consumes it.
 */
class CrashReportPopup(private val settings: Settings, report: String) :
	MessagePopup(report.substringBefore("\n\n"), report) {

	override fun onDismissed() {
		settings.remove("uncaughtException")
	}
}
