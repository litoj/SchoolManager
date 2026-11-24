package cz.litoj.schlmgr.data

import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.CounterChain
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.GraphDelete
import cz.litoj.schlmgr.data.db.GraphWalk
import cz.litoj.schlmgr.data.db.ImportService
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.LinkOrder
import cz.litoj.schlmgr.data.db.MoveRequest
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.dao.ItemDao

enum class MoveResult { MOVED, REFUSED_CYCLE, REFUSED_OTHER_SUBJECT, ALREADY_PRESENT }

/**
 * The link facts that decide whether one paste row can land.
 * [inside] follows reference links like the write guards do.
 */
data class PasteFacts(
	val present: Boolean,
	val reference: Boolean,
	val inside: Boolean,
	val otherSet: Boolean,
)

/** Existing-row moves, references and subject merges, with caller-owned scope. */
class ItemTransferService internal constructor(private val database: ItemDatabase) {

	suspend fun move(
		childId: Int, oldParentId: Int, newParentId: Int, position: Int? = null,
	): MoveResult = database.withTransaction {
		val dao = database.itemDao()
		if (dao.subjectRootOf(childId, ROOT_ID) != dao.subjectRootOf(newParentId, ROOT_ID))
			return@withTransaction MoveResult.REFUSED_OTHER_SUBJECT
		val slot = dao.linkPosition(newParentId, childId)
		if (slot != null && !promotesReference(dao, childId, oldParentId, newParentId))
			return@withTransaction MoveResult.ALREADY_PRESENT
		if (moveInternal(dao, MoveRequest(childId, oldParentId, newParentId, position), slot))
			MoveResult.MOVED else MoveResult.REFUSED_CYCLE
	}

	suspend fun mergeSubject(sourceId: Int, targetParentId: Int): Boolean = try {
		mergeSubjectOrThrow(sourceId, targetParentId)
	} catch (_: SubjectMergeCycleException) {
		false
	}

	private class SubjectMergeCycleException : Exception()

	private suspend fun mergeSubjectOrThrow(sourceId: Int, targetParentId: Int): Boolean = database.withTransaction {
		val dao = database.itemDao()
		if (targetParentId == ROOT_ID) return@withTransaction false
		val source = dao.get(sourceId) ?: return@withTransaction false
		if (closesCycle(dao, sourceId, targetParentId)) return@withTransaction false
		val rows = dao.subgraph(sourceId)
		val byId = HashMap<Int, DbItem>(rows.size * 2)
		for (row in rows) byId[row.id] = row
		val children = HashMap<Int, MutableList<DbItem>>()
		for (link in dao.subgraphLinks(sourceId)
			.sortedWith(compareBy({ it.position }, { it.childId }))) {
			val child = byId[link.childId] ?: continue
			if (link.isReference) continue
			children.getOrPut(link.parentId) { ArrayList() }.add(child)
		}
		val items = ArrayList<StreamItem>()
		val rootItem = StreamItem(source, null)
		items.add(rootItem)
		GraphWalk.collectInto(items, rootItem, { walked ->
			val kids = children[walked.id] ?: emptyList()
			val visible = if (walked.type == ItemKind.WORD)
				kids.filter { it.type == ItemKind.TRANSLATION } else kids
			visible.map { StreamChild(it) }
		})
		val outcome = ImportService.run(dao, StreamImporter(items), targetParentId)
		val mergedOf = HashMap<Int, Int>()
		for (entry in items) mergedOf[entry.item.id] = outcome.savedRows[entry] ?: continue
		for (link in dao.subgraphChildLinks(sourceId).filter { it.isReference }) {
			val parent = mergedOf[link.parentId] ?: link.parentId
			val child = mergedOf[link.childId] ?: link.childId
			if (parent == link.parentId && child != link.childId)
				dao.deleteLink(link.parentId, link.childId)
			if (dao.linkPosition(parent, child) == null) {
				if (closesCycle(dao, child, parent)) throw SubjectMergeCycleException()
				dao.insertLink(DbLink(parent, child, dao.nextPosition(parent), isReference = true))
			}
		}
		CounterChain.recomputeCounterChain(dao, targetParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		dao.deleteLink(ROOT_ID, sourceId)
		if (dao.parentCount(sourceId) == 0) GraphDelete.delete(dao, sourceId)
		true
	}

	private class StreamImporter(override val items: List<StreamItem>) : DataImporter {
		override val references: List<StreamReference> = emptyList()
	}

	private suspend fun promotesReference(
		dao: ItemDao,
		childId: Int,
		oldParentId: Int,
		newParentId: Int,
	): Boolean = dao.linkIsReference(newParentId, childId) == true &&
		dao.linkIsReference(oldParentId, childId) != true

	private suspend fun moveInternal(
		dao: ItemDao,
		req: MoveRequest,
		promoteSlot: Int? = null,
	): Boolean {
		if (closesCycle(dao, req.childId, req.newParentId)) return false
		val stats = dao.subgraphWordCounters(req.childId)
		val plainLeave = dao.linkIsReference(req.oldParentId, req.childId) != true
		dao.deleteLink(req.oldParentId, req.childId)
		if (promoteSlot != null) dao.deleteLink(req.newParentId, req.childId)
		val shared = (stats.passed != 0 || stats.failed != 0) &&
			dao.plainLinksIntoSubtree(req.childId) > 0
		LinkOrder.linkAt(dao, req.newParentId, req.childId, req.position)
		if (promoteSlot != null) dao.setLinkPosition(req.newParentId, req.childId, promoteSlot)
		if (stats.passed == 0 && stats.failed == 0) return true
		if (shared) {
			if (plainLeave)
				CounterChain.recomputeCounterChain(dao, req.oldParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			CounterChain.recomputeCounterChain(dao, req.newParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		} else {
			if (plainLeave)
				CounterChain.shiftCounterChain(dao, req.oldParentId, -stats.passed, -stats.failed, dao::parentsPlainFirst)
			CounterChain.shiftCounterChain(dao, req.newParentId, stats.passed, stats.failed, dao::parentsPlainFirst)
		}
		return true
	}

	suspend fun pasteFacts(childIds: List<Int>, targetId: Int): List<PasteFacts> {
		val dao = database.itemDao()
		return childIds.map { childId ->
			val present = dao.linkPosition(targetId, childId) != null
			PasteFacts(
				present = present,
				reference = present && dao.linkIsReference(targetId, childId) == true,
				inside = dao.subgraphContains(childId, targetId),
				otherSet = dao.subjectRootOf(childId, ROOT_ID) != dao.subjectRootOf(targetId, ROOT_ID),
			)
		}
	}

	suspend fun reference(childId: Int, parentId: Int): Boolean = referenceAll(listOf(childId), parentId)

	suspend fun referenceAll(childIds: Collection<Int>, parentId: Int): Boolean = database.withTransaction {
		val dao = database.itemDao()
		for (childId in childIds) {
			if (dao.linkPosition(parentId, childId) == null && closesCycle(dao, childId, parentId))
				return@withTransaction false
		}
		for (childId in childIds) {
			if (dao.linkPosition(parentId, childId) != null) continue
			dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId), isReference = true))
		}
		true
	}

	private suspend fun closesCycle(dao: ItemDao, childId: Int, targetId: Int): Boolean =
		dao.subgraphContains(childId, targetId)

	suspend fun setOrder(parentId: Int, orderedChildIds: List<Int>) = database.withTransaction {
		LinkOrder.setOrder(database.itemDao(), parentId, orderedChildIds)
	}

	suspend fun reorder(parentId: Int, childId: Int, newIndex: Int) = database.withTransaction {
		LinkOrder.reorder(database.itemDao(), parentId, childId, newIndex)
	}

	suspend fun remove(childId: Int, parentId: Int?, progress: Progress? = null) = database.withTransaction {
		val dao = database.itemDao()
		if (parentId != null && isLastTranslation(dao, childId, parentId)) {
			val wordParents = dao.parentsPlainFirst(parentId)
			val plainParents = dao.plainParentsOf(parentId)
			for (parent in wordParents) dao.deleteLink(parent, parentId)
			updateChainsAfterRemoval(dao, parentId, plainParents)
			GraphDelete.delete(dao, parentId) { progress?.advance(1) }
			return@withTransaction
		}
		val refLink = parentId != null && dao.linkIsReference(parentId, childId) == true
		if (parentId != null) dao.deleteLink(parentId, childId)
		if (parentId != null && !refLink) updateChainsAfterRemoval(dao, childId, listOf(parentId))
		if (dao.parentCount(childId) == 0) GraphDelete.delete(dao, childId) { progress?.advance(1) }
	}

	private suspend fun updateChainsAfterRemoval(
		dao: ItemDao,
		removedId: Int,
		plainParents: List<Int>,
	) {
		if (plainParents.isEmpty()) return
		val stats = dao.subgraphWordCounters(removedId)
		if (stats.passed == 0 && stats.failed == 0) return
		if (dao.plainLinksIntoSubtree(removedId) > 0) {
			for (parent in plainParents)
				CounterChain.recomputeCounterChain(dao, parent, dao::subgraphWordCounters, dao::parentsPlainFirst)
			return
		}
		for (parent in plainParents)
			CounterChain.shiftCounterChain(dao, parent, -stats.passed, -stats.failed, dao::parentsPlainFirst)
	}

	private suspend fun isLastTranslation(dao: ItemDao, childId: Int, parentId: Int): Boolean =
		dao.get(childId)?.type == ItemKind.TRANSLATION &&
			dao.get(parentId)?.type == ItemKind.WORD &&
			dao.getChildren(parentId).none { it.type == ItemKind.TRANSLATION && it.id != childId }
}
