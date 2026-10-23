package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.testing.TestEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the settings fallback uses the default seconds per item.
 * - the startup restore falls back to it too.
 */
class TestEngineDefaultsTest {

	@Test
	fun `the default time per item is 18 seconds`() {
		assertEquals(18, TestEngine.getDefaultTime())
	}
}
