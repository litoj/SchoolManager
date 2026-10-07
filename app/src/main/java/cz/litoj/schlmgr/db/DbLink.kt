package cz.litoj.schlmgr.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The single M:N relation of the database. One row says "child is in parent".
 *
 * It expresses everything:
 *  - chapter → its children (chapters, words, notes)
 *  - word → its translations
 *  - an item appearing in another chapter (the former Reference — a link
 *    marked with [isReference])
 *  - a subject is a chapter with no row pointing at it as a child
 *
 * `position` is the child order inside the given parent — every parent owns
 * its own numbering, starting at 0 with no gaps. `is_reference` marks the
 * link a user placed through the Reference action: the same row appears in
 * the container without moving out of its home (see
 * [ItemRepository.reference]).
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
	// the PK starts with parent_id; the CASCADE lookup on child_id needs this index
	indices = [Index(value = ["child_id"])]
)
data class DbLink(
	@ColumnInfo(name = "parent_id") val parentId: Int,
	@ColumnInfo(name = "child_id") val childId: Int,
	val position: Int = 0,
	@ColumnInfo(name = "is_reference", defaultValue = "0") val isReference: Boolean = false,
)
