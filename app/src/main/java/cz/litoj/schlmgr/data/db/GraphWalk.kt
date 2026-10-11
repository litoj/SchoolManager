package cz.litoj.schlmgr.data.db
import cz.litoj.schlmgr.data.StreamItem

/**
 * The single pre-order graph walk behind exports and the subject merge — root first, then children
 * recursively ([ItemRepository] delegates its stream building to it so the
 * walk exists exactly once).
 */
internal object GraphWalk {

	/**
	 * Appends [ref]'s item's content below [ref] in pre-order, reporting each
	 * added item through [onItem] right away — the report order matches the
	 * stream order, as the export progress middleware requires.
	 */
	fun collectInto(
		out: MutableList<StreamItem>,
		ref: StreamItem,
		childrenOf: (DbItem) -> List<DbItem>,
		onItem: (StreamItem) -> Unit,
	) {
		for (child in childrenOf(ref.item)) {
			val childRef = StreamItem(child, ref)
			out.add(childRef)
			onItem(childRef)
			collectInto(out, childRef, childrenOf, onItem)
		}
	}
}
