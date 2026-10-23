package cz.litoj.schlmgr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ItemKind {
	CHAPTER,
	WORD,
	TRANSLATION,
	NOTE,
	/** The one invisible root. Its children are the subjects. */
	ROOT,
}

/** Reserved id of the single invisible root row: 0, safe because autoincrement starts at 1. */
const val ROOT_ID: Int = 0

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

/** Success ratio in percent, -1 when never tested. */
val DbItem.ratio: Int
	get() = if (passedTests + failedTests == 0) -1 else 100 * passedTests / (passedTests + failedTests)

val DbItem.testedCount: Int
	get() = passedTests + failedTests
