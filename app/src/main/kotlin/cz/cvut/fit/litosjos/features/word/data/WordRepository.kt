package cz.cvut.fit.litosjos.features.word.data

import cz.cvut.fit.litosjos.features.word.data.db.WordLocalDataSource
import cz.cvut.fit.litosjos.features.word.domain.Word

class WordRepository(private val local: WordLocalDataSource) {

	suspend fun insert(data: Word): Int {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		return local.insert(data)
	}

	suspend fun update(data: Word) {
		if (!data.isValid()) throw IllegalArgumentException("Missing fields")
		local.update(data)
	}
}

