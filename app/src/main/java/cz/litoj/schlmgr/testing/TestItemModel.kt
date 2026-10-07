package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemRepository

/**
 * One answered item of a test run — the DB port of the former
 * `ui/test/TestItemModel.java`.
 *
 * Unlike [TestEntry] (which only describes the tested word), this model carries
 * the run's per-item results: the typed [answer], whether it was [correct], and
 * the word's success stats re-read after grading, so the results popup shows the
 * state including the just-finished test.
 */
class TestItemModel(val entry: TestEntry) {

	/**
	 * The translations shown as the test hint — fixed for the whole run (equal to
	 * what `TestEngine.getTested` returns for this item's index).
	 */
	val translations: List<DbItem> = entry.translations()

	var correct = false

	/** The answer the user typed for this item. */
	var answer = ""

	/**
	 * The tested word's row — replaced by [refreshSf] after grading, because the
	 * entry's snapshot (and its sf columns) predates the test.
	 */
	var word: DbItem = entry.word
		private set

	/** Re-reads the word so its sf columns include the just-recorded answer. */
	fun refreshSf() {
		ItemRepository.get(entry.word.id)?.let { word = it }
	}

	override fun toString(): String = entry.toString()
}
