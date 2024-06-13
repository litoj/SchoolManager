package cz.cvut.fit.litosjos.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ItemDao {
	@Insert
	protected abstract suspend fun insert(data: DbItem): Long

	suspend fun insertWithTimestamp(data: DbItem): Int = insert(data.asInsertData()).toInt()

	@Update(entity = DbItem::class)
	protected abstract suspend fun update(data: DbItemUpdate)

	suspend fun updateWithTimestamp(data: DbItem) = update(data.asUpdateData())


	@Query("SELECT * FROM item WHERE parent_id = :id")
	protected abstract fun getByParentId(id: Int): Flow<List<DbItem>>

	@Query("SELECT * FROM item WHERE parent_id IS NULL")
	protected abstract fun getByParentRoot(): Flow<List<DbItem>>

	fun getByParent(id: Int?): Flow<List<DbItem>> =
		if (id == null) getByParentRoot() else getByParentId(id)

	@Query("SELECT * FROM item WHERE id = :id")
	abstract suspend fun get(id: Int): DbItem?

	@Query("DELETE FROM item WHERE id = :id")
	abstract suspend fun delete(id: Int)
}