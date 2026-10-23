package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount

/**
 * One tested word with its chapter path, subject root first and the tested word last.
 *
 * @property testedCount snapshot at pick time, not live
 * @property ratio snapshot success percent, -1 when untested
 */
class TestEntry(val word: DbItem, val path: List<DbItem>) {

	val testedCount: Int = word.testedCount
	val ratio: Int = word.ratio

	fun translations(): List<DbItem> = ItemRepository.translations(word.id)

	override fun toString(): String = word.name
}
