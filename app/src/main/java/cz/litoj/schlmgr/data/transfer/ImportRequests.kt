package cz.litoj.schlmgr.data.transfer

import android.net.Uri
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.db.DbItem

/**
 * The export codec choice, the strategy that the shared export path renders through.
 */
enum class ExportFormat {
	/** A word list in txt form, with every selected container wrapped as a chapter. */
	WORD,
	/** A tree JSON in HJSON form. */
	JSON;

	fun exporter(): DataExporter = when (this) {
		WORD -> WordListExporter()
		JSON -> HJsonExporter()
	}

	fun threadName(): String = when (this) {
		WORD -> "Word list export"
		JSON -> "JSON export"
	}

	fun mimeType(): String = when (this) {
		WORD -> "text/plain"
		JSON -> "application/hjson"
	}
}

data class ExportRequest(
	val uri: Uri,
	/** The selected rows of the pick, chapters, words and notes alike. */
	val rows: List<DbItem>,
	val format: ExportFormat,
)

/**
 * - it resolves at pick time on the caller thread.
 * - the workers keep that snapshot, so the anchor is the pick-time position.
 * - a later browse by the user cannot move it.
 */
sealed interface ImportTarget {
	data object Root : ImportTarget

	data class Container(val item: DbItem) : ImportTarget
}
