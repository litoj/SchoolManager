package cz.litoj.schlmgr.app

import cz.litoj.schlmgr.data.Progress
import android.net.Uri
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets


/**
 * The SAF text transport of the app: whole documents as UTF-8.
 * - reads count their bytes for the import progress bar.
 * - pure platform I/O, no file format here.
 * - codecs live behind ItemRepository, and the UI hands documents and text across.
 */
object FileText {

	/**
	 * Reads the whole document as UTF-8 text, reporting bytes to [progress].
	 * Returns null on failure through [Startup.onLoadFail], and the caller skips the file.
	 */
	@JvmStatic
	@JvmOverloads
	fun readText(source: Uri, progress: Progress? = null): String? {
		try {
			val sb = StringBuilder(4096)
			InputStreamReader(openCounted(source, progress), StandardCharsets.UTF_8).use { isr ->
				val buffer = CharArray(1024)
				var amount: Int
				var first = true
				while (isr.read(buffer).also { amount = it } != -1) {
					// the reader does not consume a BOM, so strip it
					// or the first name gains one
					val start = if (first && amount > 0 && buffer[0] == '\uFEFF') 1 else 0
					first = false
					sb.append(buffer, start, amount - start)
				}
			}
			return sb.toString()
		} catch (e: IOException) {
			Startup.onLoadFail(e, source.toString())
			return null
		}
	}

	/**
	 * Writes text as UTF-8 into the document in one pass, with nothing counted.
	 * Returns false on failure through [Startup.onSaveFail].
	 */
	@JvmStatic
	fun writeText(target: Uri, content: String): Boolean {
		try {
			val stream = GlobalDependencies.appContext.contentResolver.openOutputStream(target, "wt")
				?: throw IOException("No stream for $target")
			OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(content) }
			return true
		} catch (e: IOException) {
			Startup.onSaveFail(e, target.toString())
			return false
		}
	}

	private fun openCounted(source: Uri, progress: Progress?): InputStream {
		val stream = GlobalDependencies.appContext.contentResolver.openInputStream(source)
			?: throw IOException("No stream for $source")
		return if (progress == null) stream else ProgressInputStream(stream, progress)
	}

	private class ProgressInputStream(wrapped: InputStream, private val progress: Progress) :
		FilterInputStream(wrapped) {
		override fun read(): Int = super.read().also { if (it >= 0) progress.advance(1) }
		override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
			if (it > 0) progress.advance(it)
		}
	}
}
