package cz.cvut.fit.litosjos.features.picture.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import cz.cvut.fit.litosjos.core.data.db.DbItem

@Entity(
	tableName = "picture", foreignKeys = [ForeignKey(
		entity = DbItem::class,
		parentColumns = ["id"],
		childColumns = ["id"],
		onDelete = ForeignKey.CASCADE
	)]
)
data class DbPicture(
	@PrimaryKey val id: Int,
	val uri: String,
)

data class DbPictureGet(
	@Embedded val base: DbItem,
	val uri: String,
)