package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The direct-description limit ([ItemRow.showsDescription]): a description
 * at or under the limit shows under its row, a longer one and a blank one
 * never do. Blank lines never count toward the limit.
 */
class ShowsDescriptionTest {

	private val saved = ItemRow.descLines

	@After
	fun restoreLimit() {
		ItemRow.descLines = saved
	}

	@Test
	fun `a description at the limit shows directly`() {
		ItemRow.descLines = 2
		assertTrue(ItemRow.showsDescription("one line"))
		assertTrue(ItemRow.showsDescription("one line\ntwo lines"))
	}

	@Test
	fun `a description over the limit collapses to its icon`() {
		ItemRow.descLines = 2
		assertFalse(ItemRow.showsDescription("one line\ntwo lines\nthree lines"))
	}

	@Test
	fun `a zero limit collapses every description to its icon`() {
		ItemRow.descLines = 0
		assertFalse(ItemRow.showsDescription("one line"))
	}

	@Test
	fun `a blank description never shows directly`() {
		ItemRow.descLines = 5
		assertFalse(ItemRow.showsDescription(""))
		assertFalse(ItemRow.showsDescription(" \n "))
	}

	@Test
	fun `blank lines never count toward the limit`() {
		ItemRow.descLines = 2
		assertTrue(ItemRow.showsDescription("one line\n\n two lines \n\n"))
	}
}
