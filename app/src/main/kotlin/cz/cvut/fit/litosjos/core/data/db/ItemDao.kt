package cz.cvut.fit.litosjos.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val ALL_TYPES = "item LEFT JOIN word USING (id) LEFT JOIN picture USING (id)"

@Dao
abstract class ItemDao {
	@Insert
	protected abstract suspend fun insert(data: DbItem): Long

	suspend fun insertWithTimestamp(data: DbItemComplete): Int = insert(data.asInsertData()).toInt()

	@Update(entity = DbItem::class)
	protected abstract suspend fun update(data: DbItemUpdate)

	suspend fun updateWithTimestamp(data: DbItemComplete) = update(data.asUpdateData())

	@Query("SELECT * FROM $ALL_TYPES WHERE parent_id = :id")
	protected abstract fun getByParentId(id: Int): Flow<List<DbItemComplete>>

	@Query("SELECT * FROM $ALL_TYPES WHERE parent_id IS NULL")
	protected abstract fun getByParentRoot(): Flow<List<DbItemComplete>>

	fun getByParent(id: Int?): Flow<List<DbItemComplete>> =
		if (id == null) getByParentRoot() else getByParentId(id)

	@Query("SELECT * FROM $ALL_TYPES WHERE id = :id")
	abstract suspend fun get(id: Int): DbItemComplete?

	@Query("DELETE FROM item WHERE id = :id")
	abstract suspend fun delete(id: Int)
}