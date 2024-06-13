package cz.cvut.fit.litosjos.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
	tableName = "item", foreignKeys = arrayOf(
		ForeignKey(
			entity = DbItem::class,
			parentColumns = arrayOf("id"),
			childColumns = arrayOf("parent_id"),
			onDelete = ForeignKey.CASCADE
		)
	)
)
data class DbItem(
	@PrimaryKey(autoGenerate = true) val id: Int,
	@ColumnInfo(name = "parent_id") val parentId: Int?,
	val name: String,
	val description: String,
	@ColumnInfo(name = "passed_tests") val passedTests: Short,
	@ColumnInfo(name = "failed_tests") val failedTests: Short,
	val type: String, // for simpler icon + complete-object type determination
	@ColumnInfo(name = "created_at") val createdAt: Long, // for sync with server
	@ColumnInfo(name = "modified_at") val modifiedAt: Long, // -||-
//	val uri: String? = null, // picture data
//	val translation: String? = null, // word data
)

data class DbItemUpdate(
	val id: Int,
	@ColumnInfo(name = "parent_id") val parentId: Int?,
	val name: String,
	val description: String,
	@ColumnInfo(name = "passed_tests") val passedTests: Short,
	@ColumnInfo(name = "failed_tests") val failedTests: Short,
	@ColumnInfo(name = "modified_at") val modifiedAt: Long,
//	val uri: String? = null,
//	val translation: String? = null,
)

fun DbItem.asUpdateData(modifiedAt: Long = System.currentTimeMillis()) = DbItemUpdate(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests,
	failedTests = failedTests,
	modifiedAt = modifiedAt,
//	uri = uri,
//	translation = translation,
)

fun DbItem.asInsertData() =
	copy(createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis())