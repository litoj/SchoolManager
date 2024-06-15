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
	@ColumnInfo(name = "created_at") val createdAt: Long, // for sync with server
	@ColumnInfo(name = "modified_at") val modifiedAt: Long, // -||-
)

data class DbItemComplete(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	@ColumnInfo(name = "parent_id") val parentId: Int? = null,
	val name: String = "",
	val description: String = "",
	@ColumnInfo(name = "passed_tests") val passedTests: Short = 0,
	@ColumnInfo(name = "failed_tests") val failedTests: Short = 0,
	@ColumnInfo(name = "created_at") val createdAt: Long = 0, // for sync with server
	@ColumnInfo(name = "modified_at") val modifiedAt: Long = 0, // -||-
	val uri: String? = null, // picture data
	val translation: String? = null, // word data
)

data class DbItemUpdate(
	val id: Int,
	@ColumnInfo(name = "parent_id") val parentId: Int?,
	val name: String,
	val description: String,
	@ColumnInfo(name = "passed_tests") val passedTests: Short,
	@ColumnInfo(name = "failed_tests") val failedTests: Short,
	@ColumnInfo(name = "modified_at") val modifiedAt: Long,
)

fun DbItemComplete.asUpdateData(modifiedAt: Long = System.currentTimeMillis()) = DbItemUpdate(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests,
	failedTests = failedTests,
	modifiedAt = modifiedAt,
)

fun DbItemComplete.asInsertData() = DbItem(
	id = id,
	parentId = parentId,
	name = name,
	description = description,
	passedTests = passedTests,
	failedTests = failedTests,
	createdAt = System.currentTimeMillis(),
	modifiedAt = System.currentTimeMillis(),
)