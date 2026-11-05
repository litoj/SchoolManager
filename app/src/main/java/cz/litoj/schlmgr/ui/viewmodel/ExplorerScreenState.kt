package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchFilter

/** Immutable screen state, where the query lives in [SearchInput]. */
data class ExplorerScreenState(
	val breadcrumbs: List<String> = emptyList(),
	val info: InfoBar? = null,
	val isInfoExpanded: Boolean = false,
	val reorderable: Boolean = false,
	val searchUi: SearchUi = SearchUi.OFF,
	val sibling: SiblingSwitch? = null,
	/**
	 * How many content loads landed, which the slide waits on.
	 * - the slide does not wait on list identity.
	 */
	val contentStep: Int = 0,
)

data class SiblingSwitch(val step: Int, val dir: Int)

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
	 * @param filter the active structured filter, null for plain text
	 * @param done false while the run still streams, so an empty count reads as running
	 */
	data class Search(
		val count: Int,
		val filter: SearchFilter? = null,
		val done: Boolean = true,
	) : InfoBar

	/** A malformed query, so the empty list does not read as no results. */
	data object InvalidSearch : InfoBar

	/** A regex that does not compile, its detail goes to the notice. */
	data object InvalidPattern : InfoBar
}
