package cz.litoj.schlmgr.data
import cz.litoj.schlmgr.data.db.DbItem

/**
 * One conversion-stream item — the row id is ignored, saving assigns one.
 * A `null` parent anchors to the import target or the exported container.
 *
 * The import's indexes key on the instance, never value equality.
 */
class StreamItem(val item: DbItem, val parent: StreamItem?)

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
 * - [StreamItem]s in pre-order (parents before children), plus the
 *   references to resolve after saving.
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
