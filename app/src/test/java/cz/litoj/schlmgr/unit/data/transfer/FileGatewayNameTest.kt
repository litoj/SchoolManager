package cz.litoj.schlmgr.unit.data.transfer

import cz.litoj.schlmgr.data.transfer.FileGateway
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The source-container name math of a picked word-list file
 * ([FileGateway.containerNameFrom]) — the suffix cut and its fallbacks. The
 * name only labels the parse errors — the content itself lands with no wrapper.
 *
 * Pure JVM — Robolectric's SAF query names no document, so the import workers
 * never reach a real name there.
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
		// only the last dot splits — the rest of the name stays whole
		assertEquals("a.b", FileGateway.containerNameFrom("a.b.txt"))
	}

	@Test
	fun `a name without a dot stays whole`() {
		assertEquals("bare", FileGateway.containerNameFrom("bare"))
	}
}
