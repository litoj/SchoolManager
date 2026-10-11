package cz.litoj.schlmgr.testing

/** Per-second test callback. */
fun interface Timer {

	/**
	 * Tick callback, last on time-out. Runs on the timer thread (UI work
	 * must post to main) — `false` ends the countdown.
	 */
	fun doOnSec(secsLeft: Int): Boolean
}
