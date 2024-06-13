package cz.cvut.fit.litosjos.features.picture.data.db

import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.core.data.db.toDomain
import cz.cvut.fit.litosjos.features.picture.domain.Picture

class PictureLocalDataSource(
	private val dao: PictureDao, private val itemSource: ItemLocalDataSource
) {
	suspend fun insert(data: Picture): Int {
		val itemId = itemSource.insert(data.base)
		dao.insert(itemId, data.uri)
		return itemId
	}

	suspend fun update(data: Picture) {
		itemSource.update(data.base) // same use-case as in `insert`
		dao.update(DbPicture(data.base.id, data.uri))
	}

	suspend fun getBy(id: Int): Picture? = dao.getBy(id)?.let {
		Picture(it.base.toDomain(), it.uri)
	}
}