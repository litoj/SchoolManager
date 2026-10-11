package cz.litoj.schlmgr.data.transfer

import android.net.Uri
import cz.litoj.schlmgr.data.json.HJsonExporter
import cz.litoj.schlmgr.data.wordlist.WordListExporter
import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.db.DbItem

/**
 * The export codec choice — the strategy the shared export path renders through.
 */
enum class ExportFormat {
	/** A word list (`.txt`) — every selected container wrapped as a chapter. */
	WORD,
	/** A tree JSON (HJSON) — the containers rendered with their names. */
	JSON;

	fun exporter(): DataExporter = when (this) {
		WORD -> WordListExporter()
		JSON -> HJsonExporter()
	}

	fun threadName(): String = when (this) {
		WORD -> "Word list export"
		JSON -> "JSON export"
	}
}

data class ExportRequest(
	val uri: Uri,
	val containers: List<DbItem>,
	val format: ExportFormat,
)

/**
 * Where an import lands — the open container, or the subjects root.
 * Resolved at pick time on the caller thread — the workers keep that
 * snapshot, so the anchor is the picked-time position whatever the
 * user browses to before the run.
 */
sealed interface ImportTarget {
	data object Root : ImportTarget

	data class Container(val item: DbItem) : ImportTarget
}
