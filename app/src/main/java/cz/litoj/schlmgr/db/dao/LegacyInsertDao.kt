package cz.litoj.schlmgr.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.DbLink

/**
 * Direct-insert API used by the legacy JSON migration loader: parse a node,
 * insert it as an [DbItem] + one [DbLink] to its parent, recurse on children.
 * Never touches engine objects.
 */
@Dao
abstract class LegacyInsertDao {

	@Insert
	abstract suspend fun insertItem(item: DbItem): Long

	@Insert
	abstract suspend fun insertLink(link: DbLink)

	@Query("DELETE FROM item")
	abstract suspend fun deleteAllItems()

	@Query("DELETE FROM item_link")
	abstract suspend fun deleteAllLinks()
}
