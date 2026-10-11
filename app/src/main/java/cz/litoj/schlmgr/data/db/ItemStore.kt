package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.WordDao

/**
 * Storage for item-add merge.
 * - DAO form for interactive adds, session form for bulk.
 * - session in-memory form avoids per-item recursive SQL.
 */
internal interface ItemStore {

	suspend fun row(id: Int): DbItem?

	/** Subject scope — root-child ancestor, itself included. Arbitrary for multi-path rows. */
	suspend fun subjectRootOf(itemId: Int): Int?

	suspend fun findInSubject(scope: Int, name: String, kind: ItemKind): DbItem?

	suspend fun insert(row: DbItem): DbItem

	suspend fun mergeDescription(row: DbItem, incoming: String?): DbItem

	/**
	 * Adopts the more-tested counters onto a merged row — every merge
	 * site's rule (no change when the row's own are the more tested).
	 */
	suspend fun mergeCounters(row: DbItem, passed: Int, failed: Int)

	suspend fun linked(parentId: Int, childId: Int): Boolean

	suspend fun linkAtEnd(parentId: Int, childId: Int)
}

/** DAO-backed store for interactive adds. */
internal class DaoItemStore(private val dao: ItemDao, private val wordDao: WordDao) : ItemStore {

	override suspend fun row(id: Int): DbItem? = dao.get(id)

	override suspend fun subjectRootOf(itemId: Int): Int? = dao.subjectRootOf(itemId, ROOT_ID)

	override suspend fun findInSubject(scope: Int, name: String, kind: ItemKind): DbItem? =
		// Root scope is direct children only — recursive query would span every subject.
		if (scope == ROOT_ID)
			dao.getChildren(ROOT_ID).firstOrNull { it.name == name && it.type == kind }
		else wordDao.findWordInSubject(scope, name, kind)

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
