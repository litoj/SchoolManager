package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.db.DbItem

/**
 * The stats re-read after grading, so the results popup shows the just-finished state.
 */
class TestItemModel(val entry: TestEntry) {

	/**
	 * The translations shown as the test hint, fixed for the whole run.
	 */
	val translations: List<DbItem> = entry.translations()

	var correct = false

	/** Raw field values stay separate, so result marking never treats their separator as typed text. */
	var answers: List<String> = emptyList()

	var answer: String
		get() = answers.joinToString("/")
		set(value) {
			answers = listOf(value)
		}

	/**
	 * The row of the tested word, replaced by [refreshStats] after grading.
	 * The snapshot of the entry, with its tested count and ratio, predates the test.
	 */
	var word: DbItem = entry.word
		private set

	/** Applies the refreshed row so its tested count and ratio include the recorded answer. */
	fun refreshStats(refreshed: DbItem) {
		word = refreshed
	}

	override fun toString(): String = entry.toString()
}
