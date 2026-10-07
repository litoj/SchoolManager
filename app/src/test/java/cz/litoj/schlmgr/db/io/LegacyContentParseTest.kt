package cz.litoj.schlmgr.db.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the old-JSON parser [LegacyContent.Reader]: the happy path
 * and the truncated-source handling — a cut-off file must report a
 * positioned error instead of crashing on the missing character.
 */
class LegacyContentParseTest {

	@Test
	fun parsesACompleteObject() {
		val node = LegacyContent.Reader("""{"name":"Subject","s":51,"f":5}""").mContent
		assertEquals("Subject", node.name())
		assertEquals(51, node.passed())
		assertEquals(5, node.failed())
	}

	@Test
	fun parsesNestedChildren() {
		val node = LegacyContent.Reader(
			"""{"name":"p","cdrn":[{"name":"c1"},{"name":"c2"}]}"""
		).mContent
		assertEquals(listOf("c1", "c2"), node.children().map { it.name() })
	}

	@Test
	fun anEmptySourceIsRejected() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			LegacyContent.Reader("").mContent
		}
		assertTrue(error.message!!.contains("Source ended"))
	}

	@Test
	fun aTruncatedStringReportsAnError() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			LegacyContent.Reader("""{"name":"cut""").mContent
		}
		assertTrue(error.message!!.contains("Source ended"))
	}

	@Test
	fun aTruncatedArrayReportsAnError() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			LegacyContent.Reader("""{"name":"x","cdrn":[{"name""").mContent
		}
		assertTrue(error.message!!.contains("Source ended"))
	}
}
