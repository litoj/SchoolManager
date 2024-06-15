package cz.cvut.fit.litosjos.core.data.db

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.features.chapter.domain.Chapter
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.subject.domain.Subject
import cz.cvut.fit.litosjos.features.word.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ItemLocalDataSource(private val dao: ItemDao) {

	suspend fun insert(data: Item): Int = dao.insertWithTimestamp(data.toDb())

	suspend fun update(data: Item) = dao.updateWithTimestamp(data.toDb())

	fun getByParent(id: Int?): Flow<List<Item>> = dao.getByParent(id).map { list ->
		list.map { item -> item.toDomain() }
	}

	suspend fun get(id: Int): Item? = dao.get(id)?.toDomain()

	suspend fun delete(id: Int) = dao.delete(id)
}

fun DbItemComplete.toDomain(): Item {
	val base = Item(
		id = id,
		parentId = parentId,
		name = name,
		description = description,
		passedTests = passedTests.toInt(),
		failedTests = failedTests.toInt(),
	)

	return when {
		parentId == null -> Subject(base)
		uri != null -> Picture(base, uri)
		translation != null -> Word(base, translation)
		else -> Chapter(base)
	}
}

fun Item.toDb() = DbItemComplete(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests.toShort(),
	failedTests = failedTests.toShort(),
)