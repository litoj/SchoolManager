package cz.cvut.fit.litosjos.features.word.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

@Dao
abstract class WordDao {
	@Query("INSERT INTO word (id, translation) VALUES (:id, :uri)")
	abstract suspend fun insert(id: Int, uri: String)

	@Update
	abstract suspend fun update(data: DbRawWord)

	@Query("SELECT * FROM item JOIN word USING (id) WHERE id = :id")
	abstract suspend fun get(id: Int): DbWordGet?

	@Query("SELECT translation FROM word WHERE id = :id")
	abstract suspend fun getRaw(id: Int): String
}