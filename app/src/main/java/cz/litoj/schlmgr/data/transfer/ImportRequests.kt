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

	/** The suffix of a file name of this format, a tree JSON carries none. */
	fun suffix(): String = when (this) {
		WORD -> ".txt"
		JSON -> ""
	}
}

/**
 * Where an export lands.
 * - a document is the picked file, a share the cache copy of the share sheet.
 */
sealed interface ExportDestination {
	/** The picked document, its own name names the report. */
	data class Document(val uri: Uri) : ExportDestination

	/** The cache copy of a share, [fileName] is the name the receiver sees. */
	data class Share(val fileName: String) : ExportDestination
}

/**
 * - it resolves at pick time on the caller thread.
 * - the workers keep that snapshot, so the anchor is the pick-time position.
 * - a later browse by the user cannot move it.
 */
sealed interface ImportTarget {
	data object Root : ImportTarget

	data class Container(val item: DbItem) : ImportTarget
}
