package cz.litoj.schlmgr.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * The kind of a row in the single `item` table. Stored as the lowercase name
 * ('chapter' | 'word' | 'translation' | 'note' | 'root') via [ItemKindConverter].
 *
 * Every subject is a chapter linked to the single invisible [ROOT] row
 * ([cz.litoj.schlmgr.db.ItemRepository.ROOT_ID]), so a subject is an ordinary
 * child of a container just like any other item.
 */
enum class ItemKind {
	CHAPTER,
	WORD,
	TRANSLATION,
	NOTE,
	/** The one invisible root; its children are the subjects. */
	ROOT,
}

class ItemKindConverter {
	@TypeConverter
	fun toDb(value: ItemKind): String = value.name.lowercase()

	@TypeConverter
	fun fromDb(value: String): ItemKind = ItemKind.valueOf(value.uppercase())
}

@Entity(tableName = "item")
data class DbItem(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	val name: String,
	val description: String,
	val type: ItemKind,
	@ColumnInfo(name = "passed_tests") val passedTests: Int = 0,
	@ColumnInfo(name = "failed_tests") val failedTests: Int = 0,
)

/** Success ratio in percent, `-1` when never tested (engine `BasicData.getRatio`). */
val DbItem.ratio: Int
	get() = if (passedTests + failedTests == 0) -1 else 100 * passedTests / (passedTests + failedTests)

/** How many tests were run on this item. */
val DbItem.sfCount: Int
	get() = passedTests + failedTests
