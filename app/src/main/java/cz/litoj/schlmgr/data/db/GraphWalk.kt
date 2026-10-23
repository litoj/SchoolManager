package cz.litoj.schlmgr.data.db
import cz.litoj.schlmgr.data.StreamChild
import cz.litoj.schlmgr.data.StreamItem

/**
 * The single pre-order graph walk behind exports and the subject merge.
 * - [ItemRepository] delegates its stream building here, so the walk exists exactly once.
 */
internal object GraphWalk {

	/**
	 * - a reference child with [StreamChild.refPath] set appends without its content.
	 * - the row appears at its plain home instead.
	 */
	fun collectInto(
		out: MutableList<StreamItem>,
		ref: StreamItem,
		childrenOf: (DbItem) -> List<StreamChild>,
	) {
		for (child in childrenOf(ref.item)) {
			val childRef = StreamItem(child.row, ref, child.refPath)
			out.add(childRef)
			if (child.refPath == null) collectInto(out, childRef, childrenOf)
		}
	}
}
