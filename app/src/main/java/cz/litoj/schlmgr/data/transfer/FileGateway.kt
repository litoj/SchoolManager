package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.Progress
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import java.nio.charset.StandardCharsets

/**
 * The import/export document boundary:
 * - name lookups.
 * - the titled progress bars.
 * - the export write with its success report.
 */
object FileGateway {

	/**
	 * The picked document's name, or a fallback when the provider names nothing — the
	 * empty name for an import file (the suffix math tolerates it), the raw uri for a
	 * human-facing report.
	 */
	fun displayName(ctx: Context, uri: Uri, fallback: String = uri.toString()): String =
		DocumentFile.fromSingleUri(ctx, uri)?.name ?: fallback

	/**
	 * The source-container name of a picked word-list file — the name without
	 * its suffix, `"subject"` when the name is null or empty. It only labels
	 * the parse errors — the content lands with no wrapper of its own.
	 */
	fun containerNameFrom(pickedName: String?): String {
		if (pickedName.isNullOrEmpty()) return "subject"
		val dot = pickedName.lastIndexOf('.')
		return if (dot > 0) pickedName.substring(0, dot) else pickedName
	}

	/**
	 * The picked document's own shape — its first non-whitespace byte is `[` or `{`.
	 * The tree JSON always starts there; a word list starts with a name.
	 *
	 * An unreadable document reads as a word list — the worker's own read reports the failure.
	 */
	fun startsAsJson(ctx: Context, uri: Uri): Boolean {
		val stream = try {
			ctx.contentResolver.openInputStream(uri)
		} catch (e: Exception) {
			return false
		} ?: return false
		return stream.use {
			var byte = it.read()
			while (byte != -1 && Character.isWhitespace(byte.toChar())) byte = it.read()
			byte == '['.code || byte == '{'.code
		}
	}

	fun importProgress(ctx: Context, labelRes: Int, uri: Uri): FileProgress {
		val file = DocumentFile.fromSingleUri(ctx, uri)
		val progress = FileProgress(ctx.getString(labelRes) + (file?.name ?: uri.toString()))
		progress.bytes(file?.length() ?: 0)
		return progress
	}

	fun titledProgress(ctx: Context, labelRes: Int, name: String): FileProgress =
		FileProgress(ctx.getString(labelRes) + name)

	/**
	 * Parses one subject directory from its files — the texts come off [dir]
	 * through [FileText], so the bytes that flow report to [progress].
	 */
	fun loadLegacySubject(
		dir: DocumentFile, progress: FileProgress,
	): LegacyJsonLoader.LegacySubject? {
		val name = dir.name ?: return null
		return LegacyJsonLoader.parseSubjectDir(DocSource(dir, progress), name)
	}

	/**
	 * One subject directory from its files as a [LegacyFileSource] — a missing or
	 * empty file reads as nothing, an unreadable one reports its own failure.
	 */
	private class DocSource(
		private val dir: DocumentFile,
		private val progress: Progress,
	) : LegacyFileSource {
		override fun read(relativePath: String): String? {
			// findFile, never a creating lookup — a missing file must not be born here
			var file = dir
			for (segment in relativePath.split('/')) {
				if (segment.isEmpty()) continue
				file = file.findFile(segment) ?: return null
			}
			if (file.isDirectory || file.length() == 0L) return null
			return FileText.readText(file.uri, progress)
		}
	}

	fun utf8Size(text: String): Long =
		text.toByteArray(StandardCharsets.UTF_8).size.toLong()

	/**
	 * Writes one finished export to the picked target — the bytes that flow
	 * out re-target the bar from the walked rows to the write, and the
	 * success report follows only a written file — a failure already went
	 * out through `onSaveFail`.
	 */
	fun writeExport(
		progress: FileProgress, uri: Uri, name: String,
		text: String, words: Int, chapters: Int,
	) {
		progress.bytes(utf8Size(text))
		if (FileText.writeText(uri, text, progress = progress)) {
			Startup.onExportSuccess(name, words, chapters)
		}
	}
}
