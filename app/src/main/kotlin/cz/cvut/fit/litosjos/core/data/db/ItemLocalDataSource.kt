package cz.cvut.fit.litosjos.core.data.db

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ItemLocalDataSource(private val dao: ItemDao) {

	suspend fun insert(data: Item): Int = dao.insertWithTimestamp(data.toTrimmedDb())

	suspend fun update(data: Item) = dao.updateWithTimestamp(data.toTrimmedDb())

	fun getByParent(id: Int?): Flow<List<Item>> = dao.getByParent(id).map { list ->
		list.map { item -> item.toDomain() }
	}

	suspend fun get(id: Int): Item? = dao.get(id)?.toDomain()

	suspend fun delete(id: Int) = dao.delete(id)
}

private fun Item.toTrimmedDb() = DbItem(
	id = id,
	parentId = parentId,
	name = name.trim(),
	description = description.trim(),
	passedTests = passedTests.toShort(),
	failedTests = failedTests.toShort(),
	type = type.name,
	createdAt = 0,
	modifiedAt = 0,
)

fun DbItem.toDomain() = Item(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests.toInt(),
	failedTests = failedTests.toInt(),
	type = ItemType.valueOf(type),
)

fun Item.toDb() = DbItem(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests.toShort(),
	failedTests = failedTests.toShort(),
	type = type.name,
	createdAt = 0,
	modifiedAt = 0,
)