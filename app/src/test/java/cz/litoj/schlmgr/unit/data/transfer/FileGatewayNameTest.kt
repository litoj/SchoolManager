package cz.litoj.schlmgr.unit.data.transfer

import cz.litoj.schlmgr.data.transfer.FileGateway
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the name only labels the parse errors.
 * - the content itself lands with no wrapper.
 * - the tests are pure JVM.
 * - the SAF query of Robolectric names no document, so the workers never see a name.
 */
class FileGatewayNameTest {

	@Test
	fun `a null or empty name falls back to subject`() {
		assertEquals("subject", FileGateway.containerNameFrom(null))
		assertEquals("subject", FileGateway.containerNameFrom(""))
	}

	@Test
	fun `the suffix after the last dot is cut`() {
		assertEquals("My words", FileGateway.containerNameFrom("My words.txt"))
		// only the last dot splits, and the rest of the name stays whole
		assertEquals("a.b", FileGateway.containerNameFrom("a.b.txt"))
	}

	@Test
	fun `a name without a dot stays whole`() {
		assertEquals("bare", FileGateway.containerNameFrom("bare"))
	}
}
