package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion

/** Immutable screen state, where the query lives in [SearchInput]. */
data class ExplorerScreenState(
	val breadcrumbs: List<String> = emptyList(),
	val info: InfoBar? = null,
	val isInfoExpanded: Boolean = false,
	val reorderable: Boolean = false,
	val searchUi: SearchUi = SearchUi.OFF,
	/** How many content loads landed, which resets the row gestures and floating action button. */
	val contentStep: Int = 0,
)

/**
 * The search mode, which decides what the user sees of the search.
 * - the field renders while [TYPING].
 */
enum class SearchUi {
	OFF,

	TYPING,

	RESULTS,
}

/** The info strip semantics, where the view resolves the text. */
sealed interface InfoBar {
	/** @param kinds the row count per kind, a kind without rows stays absent */
	data class Children(val kinds: Map<ItemKind, Int>, val ratio: Int, val description: String) : InfoBar

	data class Root(val count: Int) : InfoBar

	/**
	 * @param criteria the filters of the run, empty for the typed text only
	 * @param done false while the run still streams, so an empty count reads as running
	 * @param selected the rows the run reads, 0 for the whole container
	 */
	data class Search(
		val count: Int,
		val criteria: List<Criterion> = emptyList(),
		val done: Boolean = true,
		val selected: Int = 0,
	) : InfoBar
}
