package cz.cvut.fit.litosjos.features.picture.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

@Dao
abstract class PictureDao {
	@Query("INSERT INTO picture (id, uri) VALUES (:id, :uri)")
	abstract suspend fun insert(id: Int, uri: String)

	@Update
	abstract suspend fun update(data: DbPicture)
}