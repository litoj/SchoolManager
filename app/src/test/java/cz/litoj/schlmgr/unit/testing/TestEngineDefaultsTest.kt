package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.testing.TestEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The engine's persisted defaults — the default seconds per item a fresh
 * program state carries. Pins the value the settings fallback and the
 * startup restore both fall back to.
 */
class TestEngineDefaultsTest {

	@Test
	fun `the default time per item is 18 seconds`() {
		assertEquals(18, TestEngine.getDefaultTime())
	}
}
