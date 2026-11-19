package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.wordVm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marks of [cz.litoj.schlmgr.ui.viewmodel.Selection] are display state of the row.
 * - so no marking step publishes a list, whatever the marks were before.
 */
class SelectionClearTest {

	@Test
	fun `marking a row publishes no list`() {
		val model = wordVm(3)
		val before = model.listState.value

		model.setSelected(model.items().first(), true)

		assertSame(before, model.listState.value)
		assertTrue(model.items().first().selected)
	}

	@Test
	fun `clearing a full selection publishes no list`() {
		val model = wordVm(3)
		model.selectAll(true)
		val before = model.listState.value

		model.clearSelection()

		assertSame(before, model.listState.value)
		assertEquals(0, model.items().count { it.selected })
	}

	@Test
	fun `a drag range marks every row it covers`() {
		val model = wordVm(4)

		model.selectRange(1, 2)

		assertEquals(listOf(false, true, true, false), model.items().map { it.selected })
		assertFalse(model.items().first().selected)
	}
}
