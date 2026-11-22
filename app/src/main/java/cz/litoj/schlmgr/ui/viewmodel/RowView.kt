package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.ItemKind

/**
 * The display state of one explorer row, what [cz.litoj.schlmgr.ui.component.Item] renders.
 * - a live row is an [ItemRow], which owns the values and publishes its own changes.
 * - a sample row is a [StaticRow], which shows a fixed example and owns nothing.
 */
interface RowView {

	/** The LazyColumn key. */
	val key: Any

	/** The 1-based index in the shown list. */
	val position: Int

	/** The displayed name, the translations of a flipped word. */
	val name: String

	/** The line under the name, empty when none. */
	val description: String

	val kind: RowKind

	/** The success percent of the row, negative when untested. */
	val ratio: Int

	/** All tests the row counts, the failure step of the band reads it. */
	val testedCount: Int

	/** The failed tests of the row, each one deepens the red band. */
	val failedTests: Int

	val isReference: Boolean

	/** Whether the reference can jump, since a second container holds the row. */
	val hasSource: Boolean

	val selected: Boolean

	/** Whether the gathered batch holds the row, which shows like a selection. */
	val batched: Boolean

	/** The displayed trail below the search root, null outside a search. */
	val trackPath: List<DbItem>?

	/** The lines of a flipped word, one per translation. */
	val translations: List<TranslationLine>
}

/**
 * A row that only displays, the example rows of the test result popups and the previews.
 * - it reaches no database and no controller, so a sample stays a sample.
 */
data class StaticRow(
	override val key: Any,
	override val position: Int,
	override val name: String,
	override val description: String = "",
	override val kind: RowKind = RowKind.WORD,
	override val ratio: Int = -1,
	override val testedCount: Int = 0,
	override val failedTests: Int = 0,
	override val isReference: Boolean = false,
	override val hasSource: Boolean = false,
	override val selected: Boolean = false,
	override val batched: Boolean = false,
	override val trackPath: List<DbItem>? = null,
	override val translations: List<TranslationLine> = emptyList(),
) : RowView

enum class ExplorerRowVariant { Browse, Search }

/**
 * Which kind of thing one row shows, the semantic identity that the UI maps to an icon.
 * - the state layer never resolves drawables, see RowKind.iconRes in the component layer.
 */
enum class RowKind { SUBJECT, NOTE, CHAPTER, WORD }

/** The kind of a database row, a root child being a subject. */
fun rowKindOf(item: DbItem, isSubject: Boolean): RowKind = when {
	isSubject -> RowKind.SUBJECT
	item.type == ItemKind.NOTE -> RowKind.NOTE
	item.type == ItemKind.CHAPTER -> RowKind.CHAPTER
	else -> RowKind.WORD
}
