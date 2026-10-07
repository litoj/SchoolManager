package cz.litoj.schlmgr.db

import org.junit.Assert.assertEquals
import org.junit.Test

/** The import-time description merge ([ItemRepository.mergeDescription]). */
class DescriptionMergeTest {

	@Test
	fun `an empty incoming value keeps the stored value`() {
		assertEquals("a", ItemRepository.mergeDescription("a", null))
		assertEquals("a", ItemRepository.mergeDescription("a", ""))
		assertEquals("", ItemRepository.mergeDescription("", null))
	}

	@Test
	fun `an empty stored value takes the incoming value`() {
		assertEquals("b", ItemRepository.mergeDescription("", "b"))
	}

	@Test
	fun `an already contained incoming value is not appended again`() {
		assertEquals("a\nb", ItemRepository.mergeDescription("a\nb", "b"))
		assertEquals("a\nb", ItemRepository.mergeDescription("a\nb", "a\nb"))
	}

	@Test
	fun `a distinct incoming value is appended after a newline`() {
		assertEquals("a\nb", ItemRepository.mergeDescription("a", "b"))
		assertEquals("a\nb\nc", ItemRepository.mergeDescription("a\nb", "c"))
	}
}
