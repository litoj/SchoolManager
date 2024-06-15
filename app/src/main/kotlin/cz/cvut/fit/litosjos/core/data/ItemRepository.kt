package cz.cvut.fit.litosjos.core.data

import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.core.domain.Item
import kotlinx.coroutines.flow.Flow

class ItemRepository(private val local: ItemLocalDataSource) {

	suspend fun insert(data: Item): Int {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		return local.insert(data)
	}

	suspend fun update(data: Item) {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		local.update(data)
	}

	fun getByParent(parentId: Int?): Flow<List<Item>> = local.getByParent(parentId)

	suspend fun get(id: Int) = local.get(id)

	suspend fun delete(id: Int) = local.delete(id)
}

