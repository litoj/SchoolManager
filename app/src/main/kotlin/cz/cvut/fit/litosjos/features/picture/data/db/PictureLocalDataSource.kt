package cz.cvut.fit.litosjos.features.picture.data.db

import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.features.picture.domain.Picture

class PictureLocalDataSource(
	private val dao: PictureDao, private val itemSource: ItemLocalDataSource
) {
	suspend fun insert(data: Picture): Int {
		val itemId = itemSource.insert(data)
		dao.insert(itemId, data.uri)
		return itemId
	}

	suspend fun update(data: Picture) {
		itemSource.update(data) // same use-case as in `insert`
		dao.update(DbPicture(data.id, data.uri))
	}
}