package cz.litoj.schlmgr.ui.viewmodel

/**
 * The state rendered by [cz.litoj.schlmgr.ui.component.ItemList].
 * - [ExplorerViewModel] maintains it.
 *
 * @property maxPosition the largest position, which drives the position column width
 * @property pickerMode picker screen, where rows stay plain and the drag always selects
 * @property selectionActive selection mode is on, by any selected row
 * @property variant the row metrics that apply, see [ExplorerRowVariant]
 * @property contentStep moves on a publish, insert or removal.
 * - not on a selection remap, drag reorder or streamed append, where indexes stay valid.
 */
data class ItemListState(
	val items: List<ItemUiModel> = emptyList(),
	val maxPosition: Int = 1,
	val pickerMode: Boolean = false,
	val selectionActive: Boolean = false,
	val variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
	val contentStep: Int = 0,
	/** A reload or a row appended at the end asks the list to show its last row. */
	val scrollToBottom: Int = 0,
) {
	/** One read for the selection strips, instead of a rescan of [items]. */
	val anySelected: Boolean get() = items.any { it.selected }

	/**
	 * Whether exactly one row is selected, since the Edit action takes a single target.
	 * - every other action accepts the whole selection.
	 */
	val singleSelected: Boolean get() = selectionCount == 1

	/** How many rows are selected, the number behind [singleSelected]. */
	val selectionCount: Int get() = items.count { it.selected }
}
