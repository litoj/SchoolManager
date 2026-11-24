package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.Startup
import java.io.File

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
	 * - a UTF-8 BOM is an encoding mark, not content.
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
			if (byte == 0xEF && it.read() == 0xBB && it.read() == 0xBF) byte = it.read()
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

	/** The grants the editor needs on a picked document. */
	private const val EDIT_GRANTS =
		Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

	/**
	 * The grant of the pick dies with the picker, so the document is held for the editor.
	 * - write is asked for first, an editor that cannot save fixes nothing.
	 * - a read-only document answers with the grant it allows.
	 */
	fun holdForEdit(ctx: Context, uri: Uri) {
		if (takeGrant(ctx, uri, EDIT_GRANTS)) return
		takeGrant(ctx, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
	}

	/** A stored import ends the file's story, so the held grant leaves with it. */
	fun releaseEdit(ctx: Context, uri: Uri) {
		val held = ctx.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
			?: return
		val grants =
			if (held.isWritePermission) EDIT_GRANTS else Intent.FLAG_GRANT_READ_URI_PERMISSION
		ctx.contentResolver.releasePersistableUriPermission(uri, grants)
	}

	private fun takeGrant(ctx: Context, uri: Uri, grants: Int): Boolean = try {
		ctx.contentResolver.takePersistableUriPermission(uri, grants)
		true
	} catch (e: SecurityException) {
		false
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

	/**
	 * The cache copy a share hands over, the older copies gone first.
	 * - the receiving app stores a name it recognises, and the folder stays one file deep.
	 */
	fun shareCopy(ctx: Context, name: String, text: String): Uri {
		val dir = File(ctx.cacheDir, SHARE_DIR)
		dir.mkdirs()
		dir.listFiles()?.forEach { it.delete() }
		val file = File(dir, shareFileName(name))
		file.writeText(text)
		return shareUri(ctx, file)
	}

	/**
	 * The door of the shared folder, the manifest file provider behind it.
	 * - the provider keeps one folder per authority for the whole process, while a test
	 *   run gets its own folder per method, so only [ShareProviderTest] uses the real door.
	 */
	@JvmStatic
	var shareUri: (Context, File) -> Uri = { ctx, file ->
		FileProvider.getUriForFile(ctx, ctx.packageName + PROVIDER_SUFFIX, file)
	}

	/** An item name as a file name: no path character, no endless length. */
	private fun shareFileName(name: String): String {
		val safe = buildString(name.length) {
			for (ch in name) append(
				if (ch in ILLEGAL_IN_NAME || ch < ' ') '_' else ch
			)
		}.trim()
		return safe.take(MAX_NAME_CHARS).trimEnd()
	}
}

private const val SHARE_DIR = "shared"
private const val PROVIDER_SUFFIX = ".fileprovider"
private const val MAX_NAME_CHARS = 80
private val ILLEGAL_IN_NAME = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
