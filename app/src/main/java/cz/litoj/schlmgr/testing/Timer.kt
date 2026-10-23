package cz.litoj.schlmgr.testing

fun interface Timer {

	/**
	 * Tick callback, last on time-out, runs on the timer thread so UI work must post to main.
	 * A false return ends the countdown.
	 */
	fun doOnSec(secsLeft: Int): Boolean
}
