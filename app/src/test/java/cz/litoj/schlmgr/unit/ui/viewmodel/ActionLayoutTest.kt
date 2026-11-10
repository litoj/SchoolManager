package cz.litoj.schlmgr.unit.ui.viewmodel

import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.viewmodel.actionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLayoutTest {

	@Test
	fun `enough space shows every action directly`() {
		val layout = actionLayout(count = 3, width = 330.dp, reservedWidth = 10.dp, slotWidth = 60.dp)
		assertEquals(3, layout.direct)
		assertTrue(!layout.showMore)
	}

	@Test
	fun `narrower space reserves one slot for every overflowing action`() {
		val layout = actionLayout(count = 5, width = 300.dp, reservedWidth = 10.dp, slotWidth = 60.dp)
		assertEquals(3, layout.direct)
		assertTrue(layout.showMore)
	}

	@Test
	fun `only one direct action needs no more button`() {
		val layout = actionLayout(count = 1, width = 100.dp, slotWidth = 60.dp)
		assertEquals(1, layout.direct)
		assertTrue(!layout.showMore)
	}

	@Test
	fun `no direct slot keeps one more slot`() {
		val layout = actionLayout(count = 3, width = 120.dp, reservedWidth = 60.dp, slotWidth = 60.dp)
		assertEquals(0, layout.direct)
		assertTrue(layout.showMore)
	}

	@Test
	fun `no slot and no action leave no button`() {
		val layout = actionLayout(count = 0, width = 120.dp, reservedWidth = 60.dp, slotWidth = 60.dp)
		assertEquals(0, layout.direct)
		assertTrue(!layout.showMore)
	}

	@Test
	fun `missing direct space still shows more`() {
		val layout = actionLayout(count = 3, width = 40.dp, reservedWidth = 96.dp, slotWidth = 60.dp)
		assertEquals(0, layout.direct)
		assertTrue(layout.showMore)
	}
}
