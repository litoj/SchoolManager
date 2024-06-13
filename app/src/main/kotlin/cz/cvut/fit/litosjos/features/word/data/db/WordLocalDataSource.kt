package cz.cvut.fit.litosjos.features.word.data.db

import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.core.data.db.toDomain
import cz.cvut.fit.litosjos.features.word.domain.Translation
import cz.cvut.fit.litosjos.features.word.domain.Word

class WordLocalDataSource(
	private val dao: WordDao, private val itemSource: ItemLocalDataSource
) {
	suspend fun insert(data: Word): Int {
		val itemId = itemSource.insert(data.base)
		// we just need the id, which won't exist -> no chance of error
		dao.insert(itemId, data.translations)
		return itemId
	}

	suspend fun update(data: Word) {
		itemSource.update(data.base) // same use-case as in `insert`
		dao.update(DbRawWord(data.base.id, data.translations))
	}

	suspend fun get(id: Int): Word? = dao.get(id)?.let {
		Word(
			it.base.toDomain(), it.translation
		)
	}

	suspend fun getRawBy(id: Int): Translation = dao.getRaw(id)
}