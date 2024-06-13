package cz.cvut.fit.litosjos.features.picture.data

import cz.cvut.fit.litosjos.features.picture.data.db.PictureLocalDataSource
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.picture.domain.isValid

class PictureRepository(private val local: PictureLocalDataSource) {

	suspend fun insert(data: Picture): Int {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		return local.insert(data)
	}

	suspend fun update(data: Picture) {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		local.update(data)
	}

	suspend fun getBy(id: Int) = local.getBy(id)
}

