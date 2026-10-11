package cz.litoj.schlmgr.ui.viewmodel

/**
 * State rendered by [cz.litoj.schlmgr.ui.component.ItemList] and
 * maintained by [ExplorerViewModel].
 *
 * @property maxPosition largest position — drives the position column width
 * @property pickerMode picker screen: selection mode stays on
 * @property selectionActive selection mode is on (picker, or any row selected)
 * @property variant which row metrics apply (see [ExplorerRowVariant])
 * @property contentStep moves on a publish, insert or removal — not on a
 *   selection remap, a drag's reorder or a streamed append (indexes stay valid)
 */
data class ItemListState(
	val items: List<ItemUiModel> = emptyList(),
	val maxPosition: Int = 1,
	val pickerMode: Boolean = false,
	val selectionActive: Boolean = false,
	val variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
	val contentStep: Int = 0,
) {
	/**
	 * Whether any row is selected — one read for the screens' selection
	 * strips instead of each rescanning [items].
	 */
	val anySelected: Boolean get() = items.any { it.selected }

	/**
	 * Whether exactly one row is selected — the strip's Edit action takes a
	 * single target. Every other action accepts the whole selection.
	 */
	val singleSelected: Boolean get() = selectionCount == 1

	/**
	 * How many rows are selected — the picker's Select enablement reads one
	 * number instead of its own count.
	 */
	val selectionCount: Int get() = items.count { it.selected }
}
