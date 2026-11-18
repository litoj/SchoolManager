package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.wordRows
import cz.litoj.schlmgr.support.wordVm
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - a bottom request belongs to the next publish, the one that carries the new rows.
 * - a row landing at the end is new content the reader has not seen.
 */
class ExplorerListScrollTest {

	@Test
	fun `an import reload asks the list to scroll to the bottom`() {
		val vm = wordVm(3)
		assertEquals(0, vm.listState.value.scrollToBottom)

		vm.requestScrollToBottom()
		vm.load(wordRows(4))

		assertEquals("the reload after the request shows the end",
			1, vm.listState.value.scrollToBottom)
	}

	@Test
	fun `a row appended by the creator asks the list to show its end`() {
		val vm = wordVm(3)
		assertEquals(0, vm.listState.value.scrollToBottom)

		vm.addItem(ItemRow(
			DbItem(name = "new", description = "", type = ItemKind.CHAPTER), null, 4))

		assertEquals("the appended row reveals the end", 1, vm.listState.value.scrollToBottom)
		assertEquals(listOf("w0", "w1", "w2", "new"), vm.items().map { it.item.name })
	}
}
