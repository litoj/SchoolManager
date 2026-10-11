package cz.litoj.schlmgr.unit.app

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.Startup.boundReport
import cz.litoj.schlmgr.app.Startup.getFirstCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The crash-report bounds ([Startup.getFirstCause], [Startup.boundReport]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@Test
	fun `a two-exception cause cycle ends instead of looping`() {
		val a = RuntimeException("a")
		val b = RuntimeException("b", a)
		a.initCause(b)
		// the deepest DISTINCT cause of the cycle — a's cause is b, b's
		// cause repeats a — the walk stops at the repeat
		assertEquals("b", getFirstCause(a))
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
