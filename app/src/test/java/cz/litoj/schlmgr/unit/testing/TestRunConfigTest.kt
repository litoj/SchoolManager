package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestRunConfig
import cz.litoj.schlmgr.testing.TestTaskModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TestRunConfigTest {

	@Test
	fun `a run keeps its answer rules after defaults change`() {
		val word = DbItem(id = 1, name = "house", description = "", type = ItemKind.WORD)
		val first = DbItem(id = 2, name = "dům", description = "", type = ItemKind.TRANSLATION)
		val second = DbItem(id = 3, name = "domov", description = "", type = ItemKind.TRANSLATION)
		val entry = TestEntry(word, listOf(word), listOf(first, second))
		val engine = TestEngine()
		val models = engine.let {
			TestEngine.applyReversed(true)
			TestEngine.applyRequireAllVariants(true)
			try {
				it.setTested(1, null, 1f, listOf(entry))
				it.getTestSrc().map(::TestItemModel)
			} finally {
				TestEngine.applyRequireAllVariants(false)
				TestEngine.applyReversed(false)
			}
		}
		try {
			val task = TestTaskModel.flatten(models, engine.getTestGroups(), engine.config).single()
			assertEquals(2, task.answerFields.size)
			assertTrue(engine.isAnswer(0, listOf("dům", "domov"), entry.translations()))
			assertEquals(listOf(first.id), engine.creditedTranslationsForRun(entry.translations(), "dům").map { it.id })
		} finally {
			TestEngine.applyRequireAllVariants(false)
			TestEngine.applyReversed(false)
		}
	}

	@Test
	fun `a run keeps its prompt groups and countdown after direction settings change`() {
		val firstWord = DbItem(id = 1, name = "shared", description = "", type = ItemKind.WORD)
		val secondWord = DbItem(id = 2, name = "shared", description = "", type = ItemKind.WORD)
		val first = TestEntry(firstWord, listOf(firstWord), listOf(
			DbItem(id = 3, name = "one/a", description = "", type = ItemKind.TRANSLATION)))
		val second = TestEntry(secondWord, listOf(secondWord), listOf(
			DbItem(id = 4, name = "two/b", description = "", type = ItemKind.TRANSLATION)))
		val entries = listOf(first, second)
		val reversed = TestEngine.isReversed
		val required = TestEngine.requireAllVariants
		val config = TestRunConfig.current().copy(reversed = true, requireAllVariants = true)
		val engine = TestEngine()
		val tick = CountDownLatch(1)
		val seconds = AtomicInteger()
		try {
			TestEngine.applyReversed(false)
			TestEngine.applyRequireAllVariants(false)
			engine.setTested(-1, {
				seconds.set(it)
				tick.countDown()
				false
			}, 1f, entries, config)

			assertEquals(1, engine.getTestGroups().size)
			engine.startTest()
			assertTrue("the first tick reports the captured time", tick.await(5, TimeUnit.SECONDS))
			assertEquals(TestEngine.testTime(1f, engine.getTestSrc(), config), seconds.get())
		} finally {
			engine.stopTest()
			TestEngine.applyReversed(reversed)
			TestEngine.applyRequireAllVariants(required)
		}
	}

	@Test
	fun `variant assignment keeps the captured ignored literals`() {
		val word = DbItem(id = 1, name = "xenon", description = "", type = ItemKind.WORD)
		val model = TestItemModel(TestEntry(word, listOf(word)))
		val config = TestRunConfig.current().copy(requireAllVariants = true, ignoredMistakes = emptyList())
		try {
			TestEngine.applyIgnoredMistakes("x")
			val task = TestTaskModel(listOf(model), 0, config)

			assertEquals(listOf("xenon"), task.assign(listOf("xenon"))[model])
		} finally {
			TestEngine.applyIgnoredMistakes("")
		}
	}
}
