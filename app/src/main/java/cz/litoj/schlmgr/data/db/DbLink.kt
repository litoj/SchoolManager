package cz.litoj.schlmgr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Single M:N relation — "child is in parent".
 * - chapter children, translations, cross-chapter links, subjects under root.
 * - position is 0-based order — deletions leave gaps, reorder compacts.
 */
@Entity(
	tableName = "item_link",
	primaryKeys = ["parent_id", "child_id"],
	foreignKeys = [
		ForeignKey(
			entity = DbItem::class,
			parentColumns = ["id"],
			childColumns = ["parent_id"],
			onDelete = ForeignKey.CASCADE
		),
		ForeignKey(
			entity = DbItem::class,
			parentColumns = ["id"],
			childColumns = ["child_id"],
			onDelete = ForeignKey.CASCADE
		),
	],
	// PK starts with parent_id — child_id needs index for CASCADE.
	indices = [Index(value = ["child_id"])]
)
data class DbLink(
	@ColumnInfo(name = "parent_id") val parentId: Int,
	@ColumnInfo(name = "child_id") val childId: Int,
	val position: Int = 0,
	@ColumnInfo(name = "is_reference", defaultValue = "0") val isReference: Boolean = false,
)
