package cz.litoj.schlmgr.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The search-input policy: query state, debounce, threshold and clearing.
 * - the shell field is a dumb view of [query].
 */
internal class SearchInput(
	private val scope: CoroutineScope,
	private val host: Host,
) {

	interface Host {

		fun runQuery(text: String)

		fun clearQuery()

		fun supersedeRun()

		fun openField()

		fun closeField()
	}

	val query = MutableStateFlow("")

	private var debounceJob: Job? = null

	private var runningQuery: String? = null

	fun open() {
		debounceJob?.cancel()
		debounceJob = null
		host.openField()
	}

	fun close() {
		debounceJob?.cancel()
		debounceJob = null
		host.closeField()
	}

	/**
	 * One keystroke batch: empty ends the run, and short text supersedes it.
	 * - longer text runs once typing pauses.
	 */
	fun onTextChange(text: String) {
		query.value = text
		debounceJob?.cancel()
		debounceJob = null
		if (text.isEmpty()) {
			runningQuery = null
			host.clearQuery()
			return
		}
		if (text.length < ExplorerViewModel.SEARCH_MIN_CHARS) {
			host.supersedeRun()
			return
		}
		val snapshot = text
		debounceJob = scope.launch {
			delay(ExplorerViewModel.SEARCH_DEBOUNCE_MS)
			if (query.value != snapshot) return@launch
			debounceJob = null
			runningQuery = snapshot
			host.runQuery(snapshot)
		}
	}

	/**
	 * Explicit submit, which bypasses the length gate.
	 * - a query the run already holds does not run again, since its results are on screen.
	 */
	fun onSubmit(text: String) {
		debounceJob?.cancel()
		debounceJob = null
		query.value = text
		if (text.isEmpty()) {
			runningQuery = null
			host.clearQuery()
		} else if (text != runningQuery) {
			runningQuery = text
			host.runQuery(text)
		}
	}

	fun cancelPending() {
		debounceJob?.cancel()
		debounceJob = null
		runningQuery = null
	}
}
