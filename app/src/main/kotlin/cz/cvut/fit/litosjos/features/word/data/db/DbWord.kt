package cz.cvut.fit.litosjos.features.word.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import cz.cvut.fit.litosjos.core.data.db.DbItem

@Entity(
	tableName = "word", foreignKeys = [ForeignKey(
		entity = DbItem::class,
		parentColumns = ["id"],
		childColumns = ["id"],
		onDelete = ForeignKey.CASCADE
	)]
)
data class DbWord(
	@PrimaryKey val id: Int,
	val translation: String,
)