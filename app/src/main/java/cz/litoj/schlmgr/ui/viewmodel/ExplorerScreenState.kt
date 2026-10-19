package cz.litoj.schlmgr.ui.viewmodel

/**
 * Immutable screen state — the query lives in [SearchInput].
 */
data class ExplorerScreenState(
	val breadcrumbs: List<String> = emptyList(),
	val info: InfoBar? = null,
	val isInfoExpanded: Boolean = false,
	val reorderable: Boolean = false,
	val searchUi: SearchUi = SearchUi.OFF,
	val sibling: SiblingSwitch? = null,
	/** How many content loads landed.
	 * - slide waits on this, not list identity.
	 */
	val contentStep: Int = 0,
)

data class SiblingSwitch(val step: Int, val dir: Int)

/**
 * Search mode — decides what the user sees of the search.
 * - field renders while [TYPING].
 */
enum class SearchUi {
	OFF,

	TYPING,

	RESULTS,
}

/** Info strip semantics — the view resolves text. */
sealed interface InfoBar {
	data class Children(val count: Int, val ratio: Int, val description: String) : InfoBar

	data class Root(val count: Int) : InfoBar

	data class Search(val count: Int, val chapters: Int) : InfoBar
}
