package cz.litoj.schlmgr.unit.data.db

import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.Descriptions
import org.junit.Assert.assertEquals
import org.junit.Test

/** The import-time description merge ([Descriptions.merge]). */
class DescriptionMergeTest {

	@Test
	fun `an empty incoming value keeps the stored value`() {
		assertEquals("a", Descriptions.merge("a", null))
		assertEquals("a", Descriptions.merge("a", ""))
		assertEquals("", Descriptions.merge("", null))
	}

	@Test
	fun `an empty stored value takes the incoming value`() {
		assertEquals("b", Descriptions.merge("", "b"))
	}

	@Test
	fun `an already contained incoming value is not appended again`() {
		assertEquals("a\nb", Descriptions.merge("a\nb", "b"))
		assertEquals("a\nb", Descriptions.merge("a\nb", "a\nb"))
	}

	@Test
	fun `a substring of a stored line is still distinct content`() {
		// containment is line-based, not substring-based — "phab" of
		// "alphabet" is different content and joins
		assertEquals("alphabet\nphab", Descriptions.merge("alphabet", "phab"))
	}

	@Test
	fun `a distinct incoming value is appended after a newline`() {
		assertEquals("a\nb", Descriptions.merge("a", "b"))
		assertEquals("a\nb\nc", Descriptions.merge("a\nb", "c"))
	}

	@Test
	fun `a multi-line value whose lines all landed is contained`() {
		// re-merging an already-merged block must not duplicate it
		assertEquals("a\nx\ny", Descriptions.merge("a\nx\ny", "x\ny"))
	}
}
