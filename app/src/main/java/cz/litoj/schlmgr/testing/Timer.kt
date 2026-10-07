package cz.litoj.schlmgr.testing

/**
 * Used for management of a running [TestEngine].
 */
fun interface Timer {

	/**
	 * This method is called every second of the test, until the time runs out.
	 *
	 * @param secsLeft seconds left to the end of the test
	 * @return if the countdown should continue
	 */
	fun doOnSec(secsLeft: Int): Boolean
}
