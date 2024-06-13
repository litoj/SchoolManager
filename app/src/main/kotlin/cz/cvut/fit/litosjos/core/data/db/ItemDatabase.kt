package cz.cvut.fit.litosjos.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cz.cvut.fit.litosjos.core.Constants.dbName
import cz.cvut.fit.litosjos.features.picture.data.db.DbPicture
import cz.cvut.fit.litosjos.features.picture.data.db.PictureDao
import cz.cvut.fit.litosjos.features.word.data.db.DbRawWord
import cz.cvut.fit.litosjos.features.word.data.db.WordDao

@Database(
	entities = [DbItem::class, DbPicture::class, DbRawWord::class],
	version = 1,
	exportSchema = false
)
abstract class ItemDatabase : RoomDatabase() {

	abstract fun itemDao(): ItemDao
	abstract fun pictureDao(): PictureDao
	abstract fun wordDao(): WordDao

	companion object {
		fun newInstance(context: Context): ItemDatabase {
			return Room.databaseBuilder(context, ItemDatabase::class.java, dbName).build()
		}
	}
}