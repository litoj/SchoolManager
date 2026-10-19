package cz.litoj.schlmgr.data
import cz.litoj.schlmgr.data.db.DbItem

/**
 * One conversion-stream item — the row id is ignored, saving assigns one.
 * A `null` parent anchors to the import target or the exported container.
 *
 * The import's indexes key on the instance, never value equality.
 *
 * A reference child carries its target's plain-home path ([refPath]).
 * Names run from the exported root down, the target's name last.
 *
 * The exporter writes it as a reference entry instead of the row content.
 * A `null` path means plain content — or the save loses the row.
 */
class StreamItem(val item: DbItem, val parent: StreamItem?, val refPath: List<String>? = null)

/**
 * One child of a walked row on the export side — the row plus, for a
 * reference link, the [StreamItem.refPath] of the same shape.
 */
class StreamChild(val row: DbItem, val refPath: List<String>? = null)

/**
 * A link from [parent] to the [targetName] item under [path] — container
 * names from the first root item down. Resolved once every stream item is saved.
 */
class StreamReference(
	val parent: StreamItem,
	val path: List<String>,
	val targetName: String,
)

/**
 * A parsed import source:
 * - [StreamItem]s in pre-order (parents before children).
 * - references to resolve after saving.
 * - implemented per file format in `cz.litoj.schlmgr.data`.
 */
interface DataImporter {
	val items: List<StreamItem>
	val references: List<StreamReference>

	/**
	 * Reports each taken entry before its row work, so skipped entries count
	 * too. A plain codec ignores this.
	 */
	fun onItem(entry: StreamItem) {}
}

/**
 * Renders the walked [StreamItem]s of one export (the repository reads the
 * rows and hands the stream over). The stream's `null`-parent items are the
 * exported containers themselves.
 */
interface DataExporter {
	fun render(items: List<StreamItem>): String

	/**
	 * Reports each walked item onto the stream. A plain codec ignores this.
	 */
	fun onItem(item: StreamItem) {}
}
