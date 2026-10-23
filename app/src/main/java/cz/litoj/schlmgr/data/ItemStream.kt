package cz.litoj.schlmgr.data
import cz.litoj.schlmgr.data.db.DbItem

/**
 * - the row id is ignored, since saving assigns one.
 * - a null parent anchors to the import target or the exported container.
 * - the import indexes key on the instance, never on value equality.
 * - a reference child carries the plain-home path of its target in [refPath].
 * - names run from the exported root down, with the target name last.
 * - the exporter writes it as a reference entry instead of the row content.
 * - a null path means plain content, or the save loses the row.
 */
class StreamItem(val item: DbItem, val parent: StreamItem?, val refPath: List<String>? = null)

/**
 * The row plus, for a reference link, the [StreamItem.refPath] of the same shape.
 */
class StreamChild(val row: DbItem, val refPath: List<String>? = null)

/**
 * The path holds container names from the first root item down, and resolves after the save.
 */
class StreamReference(
	val parent: StreamItem,
	val path: List<String>,
	val targetName: String,
)

/**
 * A parsed import source:
 * - [StreamItem] entries in pre-order, parents before children.
 * - references to resolve after the save.
 * - one implementation per file format in cz.litoj.schlmgr.data.
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
 * - the repository reads the rows and hands the stream over.
 * - the items with a null parent are the exported containers themselves.
 */
interface DataExporter {
	fun render(items: List<StreamItem>): String

	/**
	 * Hands the whole walked stream over, before [render].
	 * - the stream size sets the target of the progress bar.
	 * - each item counts as one written.
	 * - a plain codec ignores this.
	 */
	fun onStream(items: List<StreamItem>) {}
}
