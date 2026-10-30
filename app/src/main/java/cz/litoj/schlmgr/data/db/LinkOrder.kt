package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * Link placement and position math over the raw link slots.
 * - raw positions carry gaps, so a zero-based visual index maps onto the occupying slot.
 * - stateless, the repository owns the transaction.
 */
internal object LinkOrder {

	/** Places a link at a 0-based visual index, and a null position appends at the end. */
	suspend fun linkAt(dao: ItemDao, parentId: Int, childId: Int, position: Int?) {
		// position is a visual index, since raw positions carry gaps
		val links = dao.linksOf(parentId).sortedBy { it.position }
		// an out-of-range index lands at the end, like the append of a null position
		val pos = if (position != null && position in 0 until links.size) links[position].position
		else dao.nextPosition(parentId)
		// occupants from the target up make room.
		dao.shift(ShiftRange(parentId, 1, pos, dao.nextPosition(parentId) - 1))
		dao.insertLink(DbLink(parentId, childId, pos))
	}

	/**
	 * Puts the child at a 0-based index, and the slots between shift by one.
	 * Raw positions carry gaps, so the index maps onto the occupying slot.
	 */
	suspend fun reorder(dao: ItemDao, parentId: Int, childId: Int, newIndex: Int) {
		val links = dao.linksOf(parentId).sortedBy { it.position }
		val oldVisual = links.indexOfFirst { it.childId == childId }
		if (oldVisual < 0) return
		val target = newIndex.coerceIn(0, links.lastIndex)
		if (target == oldVisual) return
		val old = links[oldVisual].position
		val slot = links[target].position
		if (target < oldVisual) dao.shift(ShiftRange(parentId, 1, slot, old - 1))
		else dao.shift(ShiftRange(parentId, -1, old + 1, slot))
		dao.setLinkPosition(parentId, childId, slot)
	}

	/**
	 * Persists the manual child order, where every id takes the zero-based slot it holds.
	 * - unknown ids drop out, so stale entries cannot corrupt the order.
	 * - a repeated id keeps its first slot.
	 * - children the list never saw append at the end, since a row that landed
	 *   after the drag holds no slot of the user.
	 */
	suspend fun setOrder(dao: ItemDao, parentId: Int, orderedChildIds: List<Int>) {
		val live = LinkedHashSet(dao.linksOf(parentId).sortedBy { it.position }.map { it.childId })
		val seen = HashSet<Int>()
		val ordered = ArrayList<Int>(live.size)
		for (id in orderedChildIds) {
			if (id !in live || !seen.add(id)) continue
			ordered.add(id)
		}
		for (id in live) if (seen.add(id)) ordered.add(id)
		ordered.forEachIndexed { index, id -> dao.setLinkPosition(parentId, id, index) }
	}
}
