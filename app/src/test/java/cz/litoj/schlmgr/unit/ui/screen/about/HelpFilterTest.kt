package cz.litoj.schlmgr.unit.ui.screen.about

import cz.litoj.schlmgr.ui.screen.about.HelpFilter
import cz.litoj.schlmgr.ui.screen.about.HelpSection
import org.junit.Assert.assertEquals
import org.junit.Test

class HelpFilterTest {

	private val texts = mapOf(
		1 to "Items creation",
		2 to "Every item is created from the floating New button.",
		10 to "Chapters",
		11 to "Chapters are created from the floating New button.",
		20 to "Words",
		21 to "Words are created from the floating New button.",
		30 to "Searching",
	)

	private fun section(title: Int, body: Int = 0, vararg children: HelpSection) =
		HelpSection(title, body, children = children.toList())

	private val tree = listOf(
		section(1, 2, section(10, 11), section(20, 21)),
		section(30),
	)

	private fun visible(query: String) = HelpFilter.visible(tree, query, texts::getValue)

	@Test
	fun `a title match keeps only the section`() {
		assertEquals(setOf(30), visible("searching"))
	}

	@Test
	fun `a body match keeps the section and its ancestors`() {
		assertEquals(setOf(1, 10), visible("chapters are"))
	}

	@Test
	fun `a query matches case-insensitively`() {
		assertEquals(setOf(1, 20), visible("WORDS"))
	}

	@Test
	fun `a non-matching query drops everything`() {
		assertEquals(emptySet<Int>(), visible("nothing matches this"))
	}

	@Test
	fun `an empty query matches everything`() {
		assertEquals(setOf(1, 10, 20, 30), visible(""))
	}
}
