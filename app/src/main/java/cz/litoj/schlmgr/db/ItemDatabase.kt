package cz.litoj.schlmgr.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cz.litoj.schlmgr.db.dao.ItemDao
import cz.litoj.schlmgr.db.dao.LegacyInsertDao
import cz.litoj.schlmgr.db.dao.WordDao

@Database(
	entities = [DbItem::class, DbLink::class],
	version = 5,
	exportSchema = false
)
@TypeConverters(ItemKindConverter::class)
abstract class ItemDatabase : RoomDatabase() {

	abstract fun itemDao(): ItemDao
	abstract fun wordDao(): WordDao
	abstract fun legacyInsertDao(): LegacyInsertDao

	companion object {

		fun newInstance(context: Context): ItemDatabase {
			return Room.databaseBuilder(context, ItemDatabase::class.java, "schlmgr")
				// No migrations while the rewrite is unreleased: a version bump
				// drops the tables and the app rebuilds them from the legacy
				// subject files on the next start.
				.fallbackToDestructiveMigration(dropAllTables = true)
				.allowMainThreadQueries() // the UI layer is synchronous; see ItemRepository
				.build()
		}
	}
}
