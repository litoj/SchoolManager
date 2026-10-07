package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel.LiveSearchAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests of the search input policy ([ExplorerViewModel.classifyLiveInput]):
 * empty clears the run, short text keeps the shown list, longer text runs.
 * The debounce timing itself stays untested — only this routing is pure.
 */
class SearchInputPolicyTest {

	@Test
	fun `empty input clears the run`() {
		assertEquals(LiveSearchAction.CLEAR, ExplorerViewModel.classifyLiveInput(""))
	}

	@Test
	fun `short input keeps the shown list`() {
		assertEquals(LiveSearchAction.IGNORE, ExplorerViewModel.classifyLiveInput("a"))
		assertEquals(LiveSearchAction.IGNORE, ExplorerViewModel.classifyLiveInput("ab"))
	}

	@Test
	fun `longer input runs the search`() {
		assertEquals(LiveSearchAction.RUN, ExplorerViewModel.classifyLiveInput("abc"))
		assertEquals(LiveSearchAction.RUN, ExplorerViewModel.classifyLiveInput("abce"))
	}
}
