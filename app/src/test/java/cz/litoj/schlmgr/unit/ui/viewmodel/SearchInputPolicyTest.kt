package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.SearchInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// the unconfined dispatcher makes each debounced run land inside the test
@OptIn(ExperimentalCoroutinesApi::class)
class SearchInputPolicyTest {

	private class RecordingHost : SearchInput.Host {
		val run = ArrayList<String>()
		var cleared = 0
		var superseded = 0
		var opened = 0
		var closed = 0
		override fun runQuery(text: String) { run.add(text) }
		override fun clearQuery() { cleared++ }
		override fun supersedeRun() { superseded++ }
		override fun openField() { opened++ }
		override fun closeField() { closed++ }
	}

	@Test
	fun `empty input clears the run`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		testScheduler.advanceUntilIdle()
		input.onTextChange("")

		assertEquals(listOf("ab"), host.run)
		assertEquals("the cleared query ends the run", 1, host.cleared)
	}

	@Test
	fun `a one-character input keeps the shown list`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("a")

		// below the threshold, so no run starts and a running run is superseded
		// the shown list stays as it is instead of keeping growing
		assertEquals(0, host.run.size)
		assertEquals(0, host.cleared)
		assertEquals(1, host.superseded)
	}

	@Test
	fun `a two-character input runs the search`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		input.onTextChange("abc")
		testScheduler.advanceUntilIdle()

		assertEquals("the last text runs once", listOf("abc"), host.run)
	}

	@Test
	fun `a submit runs non-empty text at once and clears empty text`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onSubmit("a")
		assertEquals("the length gate does not apply", listOf("a"), host.run)
		input.onSubmit("")
		assertEquals(1, host.cleared)
	}

	@Test
	fun `a submit of the query the live run already ran does not re-run it`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		testScheduler.advanceUntilIdle()
		input.onSubmit("ab")

		assertEquals("the debounce already ran this query", listOf("ab"), host.run)
	}

	@Test
	fun `a submit after the flow left runs the same query again`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		testScheduler.advanceUntilIdle()
		input.cancelPending()
		input.onSubmit("ab")

		assertEquals("the run's results are gone — the submit rebuilds them", listOf("ab", "ab"), host.run)
	}

	@Test
	fun `a submit of a different query runs it`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		testScheduler.advanceUntilIdle()
		input.onSubmit("abc")

		assertEquals(listOf("ab", "abc"), host.run)
	}

	@Test
	fun `opening and closing the field cancel the pending debounce`() = runTest {
		val host = RecordingHost()
		val input = SearchInput(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), host)

		input.onTextChange("ab")
		input.open()
		assertEquals("the open cancels the pending run", 0, host.run.size)
		input.onTextChange("abc")
		input.close()
		assertEquals("the close cancels the pending run", 0, host.run.size)
		assertTrue(input.query.value == "abc")
	}
}
