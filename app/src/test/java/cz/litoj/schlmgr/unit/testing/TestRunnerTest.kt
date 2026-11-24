package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.CounterPort
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunnerTest {

	@Test
	fun `the runner owns every counter effect of an answer`() = runBlocking {
		val word = DbItem(id = 1, name = "house", description = "", type = ItemKind.WORD)
		val first = DbItem(id = 2, name = "dům", description = "", type = ItemKind.TRANSLATION)
		val second = DbItem(id = 3, name = "domov", description = "", type = ItemKind.TRANSLATION)
		val entries = listOf(TestEntry(word, listOf(word), listOf(first, second)))
		val engine = TestEngine()
		TestEngine.applyReversed(true)
		TestEngine.applyRequireAllVariants(true)
		val words = mutableListOf<Pair<Int, Boolean>>()
		val rows = mutableListOf<Pair<Collection<Int>, Boolean>>()
		val counters = object : CounterPort {
			override suspend fun bumpCounters(ids: Collection<Int>, passed: Boolean) { rows += ids to passed }
			override suspend fun bumpWordCounters(wordId: Int, passed: Boolean) { words += wordId to passed }
		}
		try {
			engine.setTested(1, null, 1f, entries)
			val runner = TestRunner(engine, counters)

			assertTrue(runner.answer(0, listOf("dům", "domov"), entries[0].translations()))

			assertEquals(listOf(word.id to true), words)
			assertEquals(listOf(listOf(first.id, second.id) to true), rows)
		} finally {
			TestEngine.applyRequireAllVariants(false)
			TestEngine.applyReversed(false)
		}
	}
}
