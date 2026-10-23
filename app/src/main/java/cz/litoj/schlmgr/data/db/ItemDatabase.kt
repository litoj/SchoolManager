package cz.litoj.schlmgr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.WordDao

@Database(
	entities = [DbItem::class, DbLink::class],
	version = 5,
	exportSchema = false
)
@TypeConverters(ItemKindConverter::class)
abstract class ItemDatabase : RoomDatabase() {

	abstract fun itemDao(): ItemDao
	abstract fun wordDao(): WordDao

	companion object {

		fun newInstance(context: Context): ItemDatabase {
			return Room.databaseBuilder(context, ItemDatabase::class.java, "schlmgr")
				// no migrations while unreleased, so a version bump drops tables.
				// the app rebuilds them from subject files on next start.
				.fallbackToDestructiveMigration(dropAllTables = true)
				// the WAL journal mode is explicit, never the AUTOMATIC heuristic.
				// - the low-RAM fallback uses TRUNCATE with one connection.
				// - a long import holds the sole connection, and every read waits behind it.
				.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
				.allowMainThreadQueries() // the UI layer is synchronous, see ItemRepository
				.build()
		}
	}
}
