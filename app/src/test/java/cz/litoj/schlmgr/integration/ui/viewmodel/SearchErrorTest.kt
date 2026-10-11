package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.support.TestExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests of the search error return — a bad regex aborts the run before it
 * starts ([ExplorerViewModel.submitSearch] shows the engine's
 * [cz.litoj.schlmgr.ui.search.SearchEngine.regexError]), stacking no hop and
 * showing no list. The message travels through the shared message flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchErrorTest {

	@Test
	fun `a bad regex shows the error and starts no run`() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val vm = TestExplorerViewModel(NavigationHistory())
		val latch = CountDownLatch(1)
		var message: GlobalDependencies.UiMessage? = null
		// the flow buffers, so the collector may attach after the emit
		thread(isDaemon = true) {
			runBlocking {
				message = GlobalDependencies.messages.first()
				latch.countDown()
			}
		}
		// the shared flow has no replay — an emit before the collector's
		// subscription lands is lost, so wait for the subscription first
		// (bounded — a collector that never starts fails the test, not hangs it)
		var waits = 0
		while (GlobalDependencies.messages.subscriptionCount.value == 0) {
			assertTrue("the collector never subscribed", waits++ < 500)
			Thread.sleep(10)
		}

		vm.submitSearch("\\r[")

		assertTrue("the error was shown", latch.await(5, TimeUnit.SECONDS))
		val notice = (message as? GlobalDependencies.UiMessage.Notice)?.notice
		assertTrue("the message is the pattern error by meaning, the detail carried as data: $message",
			notice is UiNotice.BadPattern && notice.detail.isNotEmpty())
		assertEquals("no run started", SearchUi.OFF, vm.state.value.searchUi)
		assertTrue("no hop was stacked", vm.listState.value.items.isEmpty())
		assertEquals(0, vm.history.onePath.lastOrNull())
	}
}
