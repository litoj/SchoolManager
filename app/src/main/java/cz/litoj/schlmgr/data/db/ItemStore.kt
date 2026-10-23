package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * Storage for item-add merge.
 * - the DAO form serves interactive adds, the session form serves bulk.
 * - the in-memory session form avoids per-item recursive SQL.
 */
internal interface ItemStore {

	suspend fun row(id: Int): DbItem?

	/**
	 * Subject scope, the root-child ancestor with itself included. The plain
	 * home wins, otherwise a subject that shows the row answers.
	 */
	suspend fun subjectRootOf(itemId: Int): Int?

	suspend fun findInSubject(scope: Int, name: String, kind: ItemKind): DbItem?

	suspend fun insert(row: DbItem): DbItem

	suspend fun mergeDescription(row: DbItem, incoming: String?): DbItem

	/**
	 * Adopts the more-tested counters onto a merged row.
	 * - every merge site follows this rule.
	 * - a row that is already the more tested keeps its counters.
	 */
	suspend fun mergeCounters(row: DbItem, passed: Int, failed: Int)

	suspend fun linked(parentId: Int, childId: Int): Boolean

	suspend fun linkAtEnd(parentId: Int, childId: Int)
}

internal class DaoItemStore(private val dao: ItemDao) : ItemStore {

	override suspend fun row(id: Int): DbItem? = dao.get(id)

	override suspend fun subjectRootOf(itemId: Int): Int? = dao.subjectRootOf(itemId, ROOT_ID)

	override suspend fun findInSubject(scope: Int, name: String, kind: ItemKind): DbItem? =
		// the root scope is direct children only, since a recursive query would span every subject.
		if (scope == ROOT_ID)
			dao.getChildren(ROOT_ID).firstOrNull { it.name == name && it.type == kind }
		else dao.findWordInSubject(scope, name, kind)

	override suspend fun insert(row: DbItem): DbItem = dao.get(dao.insert(row).toInt())!!

	override suspend fun mergeDescription(row: DbItem, incoming: String?): DbItem {
		val merged = Descriptions.merge(row.description, incoming)
		if (merged == row.description) return row
		dao.updateById(row.id, row.name, merged)
		return dao.get(row.id)!!
	}

	override suspend fun mergeCounters(row: DbItem, passed: Int, failed: Int) {
		if (passed + failed > row.passedTests + row.failedTests)
			dao.updateCounters(row.id, passed, failed)
	}

	override suspend fun linked(parentId: Int, childId: Int): Boolean =
		dao.linkPosition(parentId, childId) != null

	override suspend fun linkAtEnd(parentId: Int, childId: Int) =
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId)))
}
