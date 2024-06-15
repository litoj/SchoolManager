package cz.cvut.fit.litosjos.features.word.data.db

import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.features.word.domain.Word

class WordLocalDataSource(
	private val dao: WordDao, private val itemSource: ItemLocalDataSource
) {
	suspend fun insert(data: Word): Int {
		val itemId = itemSource.insert(data)
		// we just need the id, which won't exist -> no chance of error
		dao.insert(itemId, data.translations)
		return itemId
	}

	suspend fun update(data: Word) {
		itemSource.update(data) // same use-case as in `insert`
		dao.update(DbWord(data.id, data.translations))
	}
}