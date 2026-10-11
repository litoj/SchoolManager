package cz.litoj.schlmgr.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind

/**
 * Word-specific queries. The parsing of the stored word strings lives in
 * `WordNames` (pure string logic, no persistence) — this is SQL only.
 */

data class WordTranslation(@ColumnInfo(name = "word_id") val wordId: Int, @Embedded val item: DbItem)

@Dao
abstract class WordDao {

	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id = :wordId AND i.type = 'translation' ORDER BY l.position, l.child_id"
	)
	abstract suspend fun getTranslations(wordId: Int): List<DbItem>

	/**
	 * The translation rows of every word child of [parentId], each with its
	 * word — one batch read for a browse load, in link order within a word.
	 */
	@Query(
		"SELECT l.parent_id AS word_id, i.* FROM item i " +
			"INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id IN (SELECT child_id FROM item_link WHERE parent_id = :parentId) " +
			"AND i.type = 'translation' ORDER BY l.position, l.child_id"
	)
	abstract suspend fun childTranslations(parentId: Int): List<WordTranslation>

	/** A row of one kind with one name in a subject scope, `null` when
	 * none — arbitrary among several (the `LIMIT 1` pick). */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT i.* FROM item i WHERE i.id IN (SELECT id FROM des) " +
			"AND i.name = :name AND i.type = :kind LIMIT 1"
	)
	abstract suspend fun findWordInSubject(rootId: Int, name: String, kind: ItemKind = ItemKind.WORD): DbItem?
}
