package cz.litoj.schlmgr.data

import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.CounterChain
import cz.litoj.schlmgr.data.db.DaoItemStore
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.InsertWordParams
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.LinkOrder
import cz.litoj.schlmgr.data.db.NewTranslation
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.db.WordEditService
import cz.litoj.schlmgr.data.db.WordMerge

/** Item creation and edits, whose caller owns the transaction dispatcher. */
class ItemEditor internal constructor(private val database: ItemDatabase) {

	suspend fun createSubject(name: String, description: String?): DbItem {
		val itemName = name.trim()
		require(itemName.isNotEmpty()) { "A subject needs a name" }
		return database.withTransaction {
			val dao = database.itemDao()
			val item = DbItem(name = itemName, description = description?.trim() ?: "", type = ItemKind.CHAPTER)
			val id = dao.insert(item).toInt()
			LinkOrder.linkAt(dao, ROOT_ID, id, null)
			dao.get(id)!!
		}
	}

	suspend fun createChapter(parentId: Int, name: String, description: String?, position: Int? = null): DbItem =
		createChild(parentId, name, description, ItemKind.CHAPTER, position)

	suspend fun createNote(parentId: Int, name: String, description: String?, position: Int? = null): DbItem =
		createChild(parentId, name, description, ItemKind.NOTE, position)

	private suspend fun createChild(
		parentId: Int, name: String, description: String?, kind: ItemKind, position: Int?,
	): DbItem {
		val itemName = name.trim()
		require(itemName.isNotEmpty()) { if (kind == ItemKind.CHAPTER) "A chapter needs a name" else "A note needs a name" }
		return database.withTransaction {
			val dao = database.itemDao()
			val item = DbItem(name = itemName, description = description?.trim() ?: "", type = kind)
			val id = dao.insert(item).toInt()
			LinkOrder.linkAt(dao, parentId, id, position)
			dao.get(id)!!
		}
	}

	suspend fun createWord(
		parentId: Int,
		name: String,
		description: String?,
		translations: List<Pair<String, String?>>,
	): DbItem {
		require(name.trim().isNotEmpty()) { "A word needs a name" }
		require(translations.any { it.first.trim().isNotEmpty() }) {
			"A word needs at least one translation — make a note instead"
		}
		return database.withTransaction {
			val dao = database.itemDao()
			val word = WordMerge.insertWord(DaoItemStore(dao), InsertWordParams(parentId, name.trim(), description?.trim(),
				translations.map { NewTranslation(it.first.trim(), it.second?.trim()) }))
			CounterChain.recomputeCounterChain(dao, parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			word
		}
	}

	suspend fun rename(id: Int, name: String): DbItem = database.withTransaction {
		WordEditService.rename(database.itemDao(), id, name)
	}

	suspend fun setDescription(id: Int, description: String?) {
		val dao = database.itemDao()
		val item = dao.get(id) ?: return
		dao.updateById(id, item.name, description?.trim() ?: "")
	}

	suspend fun editForks(edit: WordEdit): Boolean = database.withTransaction {
		WordEditService.forks(database.itemDao(), edit)
	}

	suspend fun referencesOf(wordId: Int): List<DbItem> = database.itemDao().referenceParentsOf(wordId)

	suspend fun editWord(edit: WordEdit): DbItem = database.withTransaction {
		WordEditService.edit(database.itemDao(), edit)
	}
}
