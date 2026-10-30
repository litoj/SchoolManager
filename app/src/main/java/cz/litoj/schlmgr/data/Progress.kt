package cz.litoj.schlmgr.data

/**
 * Reports the amount that has flowed through one long file operation.
 * The app implements it with a notification bar, tests with counters.
 */
fun interface Progress {
	/** Reports more processed: bytes in the file phase, rows in the row phase. */
	fun advance(amount: Int)
}
