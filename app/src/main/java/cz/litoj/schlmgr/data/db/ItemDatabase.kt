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
				// No migrations while unreleased — version bump drops tables.
				// The app rebuilds them from subject files on next start.
				.fallbackToDestructiveMigration(dropAllTables = true)
				// WAL explicitly, never AUTOMATIC heuristic — low-RAM
				// fallback (TRUNCATE, one connection) lets long import hold
				// sole connection, so every read waits behind it.
				.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
				.allowMainThreadQueries() // the UI layer is synchronous — see ItemRepository
				.build()
		}
	}
}
