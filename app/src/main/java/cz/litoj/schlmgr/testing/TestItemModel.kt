package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.ItemRepository

/**
 * The stats re-read after grading, so the results popup shows the just-finished state.
 */
class TestItemModel(val entry: TestEntry) {

	/**
	 * The translations shown as the test hint, fixed for the whole run.
	 */
	val translations: List<DbItem> = entry.translations()

	var correct = false

	var answer = ""

	/**
	 * The row of the tested word, replaced by [refreshStats] after grading.
	 * The snapshot of the entry, with its tested count and ratio, predates the test.
	 */
	var word: DbItem = entry.word
		private set

	/** Re-reads the word so its tested count and ratio include the just-recorded answer. */
	fun refreshStats() {
		ItemRepository.get(entry.word.id)?.let { word = it }
	}

	override fun toString(): String = entry.toString()
}
