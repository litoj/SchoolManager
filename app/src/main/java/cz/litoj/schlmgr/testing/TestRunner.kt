package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.CounterPort
import cz.litoj.schlmgr.data.db.DbItem

/** Coordinates one test's grading and the counter effects of its answers. */
class TestRunner(
	engine: TestEngine,
	private val counters: CounterPort,
) {
	private val source = engine.getTestSrc()
	private val grading = engine

	suspend fun answer(index: Int, answers: List<String>, translations: List<DbItem>): Boolean {
		val correct = grading.isAnswer(index, answers, translations)
		counters.bumpWordCounters(source[index].word.id, correct)
		val texts = answers.map { it.trim() }
		if (correct) {
			val demonstrated = texts.flatMap {
				grading.creditedTranslationsForRun(translations, it)
			}.distinctBy { it.id }
			counters.bumpCounters(demonstrated.map { it.id }, true)
		} else counters.bumpCounters(translations.map { it.id }, false)
		return correct
	}
}
