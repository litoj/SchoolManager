package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * Graph deletion with the outside-parent survival rule.
 * - stateless, the repository owns the transaction.
 */
internal object GraphDelete {

	/**
	 * Deletes the graph reachable from [rootId], preserving shared items.
	 * - the walk completes before the deletion, so items with outside parents survive.
	 * - one row per statement, because of the SQLite host-parameter cap.
	 * - [onDeleted] runs after each removed row, so the caller owns the progress.
	 */
	suspend fun delete(dao: ItemDao, rootId: Int, onDeleted: (() -> Unit)? = null) {
		val doomed = HashSet<Int>()
		doomed.add(rootId)
		val stack = ArrayDeque<Int>()
		stack.addLast(rootId)
		while (stack.isNotEmpty()) {
			val parent = stack.removeLast()
			for (child in dao.childIdsOf(parent))
				if (doomed.add(child)) stack.addLast(child)
		}
		// deletable only when all parents are deletable, and parents read once until stable
		val dead = HashSet<Int>()
		dead.add(rootId)
		val parents = HashMap<Int, List<Int>>()
		var changed = true
		while (changed) {
			changed = false
			for (x in doomed) {
				if (x in dead) continue
				if (parents.getOrPut(x) { dao.parentsOf(x) }.all { it in dead }) {
					dead.add(x)
					changed = true
				}
			}
		}
		for (id in dead) {
			dao.deleteLinksTouching(id)
			dao.deleteById(id)
			onDeleted?.invoke()
		}
	}
}
