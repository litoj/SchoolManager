package cz.litoj.schlmgr.support

import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * Writes one row option per call, the rest keep their value.
 * - the door of ItemRow takes all four options at once.
 * - tests configure one field at a time.
 */
fun setRowOptions(
	parse: Boolean? = null,
	defFlip: Boolean? = null,
	descLines: Int? = null,
	doubleTapMs: Int? = null,
) {
	ItemRow.applyOptions(
		parse ?: ItemRow.parse,
		defFlip ?: ItemRow.defFlip,
		descLines ?: ItemRow.descLines,
		doubleTapMs ?: ItemRow.doubleTapMs,
	)
}
