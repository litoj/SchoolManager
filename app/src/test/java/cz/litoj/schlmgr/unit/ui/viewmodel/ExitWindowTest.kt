package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the clock is injected, so no sleep is needed.
 */
class ExitWindowTest {

	private class ExitVm(history: NavigationHistory) : TestExplorerViewModel(history) {
		var now = 1_000_000L
		init {
			clock = { now }
		}
		fun press() = registerExitPress()
	}

	private fun vm() = ExitVm(NavigationHistory())

	@Test
	fun `the root exit needs a second press inside the window`() {
		val vm = vm()

		assertTrue("first press arms the exit", vm.press())
		vm.now += 2_000
		assertFalse("still inside the window", vm.press())

		vm.now += 4_000
		assertTrue("the window closed", vm.press())
	}
}
