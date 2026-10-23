package cz.litoj.schlmgr.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind

/**
 * Word-specific queries. The parsing of stored word strings lives in WordNames,
 * which is pure string logic without persistence, so this is SQL only.
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
	 * The translation rows of every word child of [parentId], each with its word.
	 * One batch read for a browse load, in link order within a word.
	 */
	@Query(
		"SELECT l.parent_id AS word_id, i.* FROM item i " +
			"INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id IN (SELECT child_id FROM item_link WHERE parent_id = :parentId) " +
			"AND i.type = 'translation' ORDER BY l.position, l.child_id"
	)
	abstract suspend fun childTranslations(parentId: Int): List<WordTranslation>
}
