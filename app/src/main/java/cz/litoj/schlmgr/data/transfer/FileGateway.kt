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

/**
 * The import/export document boundary.
 */
object FileGateway {

	/**
	 * - an import file takes the empty name, since the suffix math tolerates it.
	 * - a human-facing report takes the raw uri.
	 */
	fun displayName(ctx: Context, uri: Uri, fallback: String = uri.toString()): String =
		DocumentFile.fromSingleUri(ctx, uri)?.name ?: fallback

	/**
	 * The shape of the picked document, read from its first non-whitespace byte.
	 * - a word list starts with a name.
	 * - an unreadable document reads as a word list, and the worker read reports the failure.
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
	 * The texts come off [dir] through [FileText], so the bytes that flow report to [progress].
	 */
	fun loadLegacySubject(
		dir: DocumentFile, progress: FileProgress,
	): LegacyJsonLoader.LegacySubject? {
		val name = dir.name ?: return null
		return LegacyJsonLoader.parseSubjectDir(DocSource(dir, progress), name)
	}

	/**
	 * A missing or empty file reads as nothing, and an unreadable one reports its own failure.
	 */
	private class DocSource(
		private val dir: DocumentFile,
		private val progress: Progress,
	) : LegacyFileSource {
		override fun read(relativePath: String): String? {
			// findFile, never a creating lookup, since a missing file must not be born here
			var file = dir
			for (segment in relativePath.split('/')) {
				if (segment.isEmpty()) continue
				file = file.findFile(segment) ?: return null
			}
			if (file.isDirectory || file.length() == 0L) return null
			return FileText.readText(file.uri, progress)
		}
	}

	/**
	 * - nothing counts the text.
	 * - the bar follows the walked objects, see [FileProgress.exporter].
	 * - the success report follows only a written file.
	 * - a failure already went out through onSaveFail.
	 */
	fun writeExport(
		uri: Uri, name: String, text: String, words: Int, chapters: Int, mime: String,
	) {
		if (FileText.writeText(uri, text)) {
			Startup.onExportSuccess(uri, name, words, chapters, mime)
		}
	}
}
