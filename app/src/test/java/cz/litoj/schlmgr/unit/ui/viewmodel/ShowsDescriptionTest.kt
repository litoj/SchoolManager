package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a description at or under the limit shows under its row.
 * - a longer one and a blank one never show.
 * - blank lines never count toward the limit.
 */
class ShowsDescriptionTest {

	private val saved = ItemRow.descLines

	@After
	fun restoreLimit() {
		setRowOptions(descLines = saved)
	}

	@Test
	fun `a description at the limit shows directly`() {
		setRowOptions(descLines = 2)
		assertTrue(ItemRow.showsDescription("one line"))
		assertTrue(ItemRow.showsDescription("one line\ntwo lines"))
	}

	@Test
	fun `a description over the limit collapses to its icon`() {
		setRowOptions(descLines = 2)
		assertFalse(ItemRow.showsDescription("one line\ntwo lines\nthree lines"))
	}

	@Test
	fun `a zero limit collapses every description to its icon`() {
		setRowOptions(descLines = 0)
		assertFalse(ItemRow.showsDescription("one line"))
	}

	@Test
	fun `a blank description never shows directly`() {
		setRowOptions(descLines = 5)
		assertFalse(ItemRow.showsDescription(""))
		assertFalse(ItemRow.showsDescription(" \n "))
	}

	@Test
	fun `blank lines never count toward the limit`() {
		setRowOptions(descLines = 2)
		assertTrue(ItemRow.showsDescription("one line\n\n two lines \n\n"))
	}
}
