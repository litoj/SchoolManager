package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.ratio
import cz.litoj.schlmgr.db.sfCount

/**
 * One tested word with its chapter path — the port of the engine's `SrcPath`.
 *
 * The path mirrors the engine shape: subject root first, the tested word
 * LAST; the word's parent container is `path[path.size - 2]`.
 *
 * @property word the tested word row
 * @property path the container chain the word was picked through (the word
 * itself as the last element)
 */
class TestEntry(val word: DbItem, val path: List<DbItem>) {

	val sfCount: Int = word.sfCount
	val ratio: Int = word.ratio

	/** The word's translations, in link order. */
	fun translations(): List<DbItem> = ItemRepository.translations(word.id)

	override fun toString(): String = word.name
}
