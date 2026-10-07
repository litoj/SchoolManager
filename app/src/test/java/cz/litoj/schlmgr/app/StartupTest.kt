package cz.litoj.schlmgr.app

import cz.litoj.schlmgr.app.Startup.boundReport
import cz.litoj.schlmgr.app.Startup.getFirstCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crash-report bounds ([Startup.getFirstCause], [Startup.boundReport]). */
class StartupTest {

	@Test
	fun `a two-exception cause cycle ends instead of looping`() {
		val a = RuntimeException("a")
		val b = RuntimeException("b", a)
		a.initCause(b)
		assertEquals("a", getFirstCause(a))
	}

	@Test
	fun `an acyclic chain still returns the innermost message`() {
		val inner = IllegalStateException("inner")
		val outer = RuntimeException("outer", RuntimeException("mid", inner))
		assertEquals("inner", getFirstCause(outer))
	}

	@Test
	fun `a chain ending in a messageless cause returns null`() {
		assertNull(getFirstCause(IllegalStateException("outer", RuntimeException())))
	}

	@Test
	fun `a small report passes through unchanged`() {
		assertEquals("short", boundReport("short"))
	}

	@Test
	fun `an oversized report is cut to the cap with a marker`() {
		val bounded = boundReport("x".repeat(256 * 1024 + 100))
		assertTrue(bounded.startsWith("x".repeat(1000)))
		assertTrue(bounded.endsWith("[truncated]"))
		assertEquals(256 * 1024 + "\n[truncated]".length, bounded.length)
	}
}
