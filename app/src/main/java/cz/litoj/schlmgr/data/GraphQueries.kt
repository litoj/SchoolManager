package cz.litoj.schlmgr.data

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.dao.WordTranslation

/** The graph rows and links that one operation reads together. */
data class GraphSnapshot(
	val items: List<DbItem>,
	val links: List<DbLink>,
	val childLinks: List<DbLink>,
)

/** Suspend graph reads, whose caller owns the dispatcher and lifecycle. */
class GraphQueries internal constructor(database: ItemDatabase) {
	private val database = database

	suspend fun ensureRoot() = database.itemDao().insertRoot(ROOT_ID)

	suspend fun children(parentId: Int): List<DbItem> = database.itemDao().getChildren(parentId)

	suspend fun subjects(): List<DbItem> = children(ROOT_ID)

	suspend fun childLinksOf(parentId: Int): List<DbLink> = database.itemDao().childLinksOf(parentId)

	suspend fun childTranslations(parentId: Int): Map<Int, List<DbItem>> =
		database.wordDao().childTranslations(parentId).groupBy(WordTranslation::wordId) { it.item }

	suspend fun get(id: Int): DbItem? = database.itemDao().get(id)

	suspend fun translations(wordId: Int): List<DbItem> = database.wordDao().getTranslations(wordId)

	suspend fun parentIds(childId: Int): List<Int> = database.itemDao().parentsOf(childId)

	suspend fun parentRows(childId: Int): List<DbItem> = database.itemDao().parentRowsOf(childId)

	suspend fun plainParentOf(childId: Int): Int? = database.itemDao().plainParentsOf(childId).firstOrNull()

	suspend fun linkIsReference(parentId: Int, childId: Int): Boolean =
		database.itemDao().linkIsReference(parentId, childId) == true

	suspend fun pathTo(parentId: Int): List<DbItem> {
		val dao = database.itemDao()
		val chain = ArrayList<DbItem>()
		var current = dao.get(parentId)
		while (current != null && current.id != ROOT_ID) {
			chain.add(current)
			current = dao.anyParentOf(current.id)
		}
		return chain.asReversed()
	}

	suspend fun snapshot(rootId: Int): GraphSnapshot {
		val dao = database.itemDao()
		return GraphSnapshot(
			dao.subgraph(rootId),
			dao.subgraphLinks(rootId),
			dao.subgraphChildLinks(rootId),
		)
	}
}
