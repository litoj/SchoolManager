package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the countdown runs on its own thread, one tick per second.
 * - a false tick return ends the countdown.
 * - the first tick runs before any sleep, so the wait only covers the thread start.
 */
class TestEngineCountdownTest {

	private fun entry(): TestEntry =
		TestEntry(DbItem(name = "dog", description = "", type = ItemKind.WORD), emptyList())

	@Test
	fun `a false tick return ends the countdown`() {
		val engine = TestEngine()
		val ticked = CountDownLatch(1)
		val ticks = AtomicInteger()
		engine.setTested(1, {
			ticks.incrementAndGet()
			ticked.countDown()
			false
		}, 3, listOf(entry()))

		engine.startTest()

		assertTrue("the countdown never ticked", ticked.await(5, TimeUnit.SECONDS))
		// one sleep of a second fits before a second tick could land
		Thread.sleep(1_500)
		assertEquals(1, ticks.get())
	}
}
