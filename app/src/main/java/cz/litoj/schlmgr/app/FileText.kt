package cz.litoj.schlmgr.app

import android.net.Uri
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets


/**
 * The app's SAF text transport — whole documents as UTF-8, with byte counts
 * for a progress bar.
	 * - pure platform I/O — no file format here.
	 * - codecs live behind `ItemRepository`; the UI hands documents and text across.
 */
object FileText {

	/**
	 * Reads the whole document as UTF-8 text, reporting bytes to [progress].
	 * Null on failure (via [Startup.onLoadFail]) — the caller skips the file.
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
					// the reader does not consume a BOM — strip it or the first name gains one
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
	 * Writes text as UTF-8 into the document, reporting bytes to [progress].
	 * False on failure (via [Startup.onSaveFail]).
	 */
	@JvmStatic
	fun writeText(
		target: Uri,
		content: String,
		progress: Progress? = null,
	): Boolean {
		try {
			OutputStreamWriter(openCountedOut(target, progress), StandardCharsets.UTF_8).use { osw ->
				osw.write(content)
			}
			return true
		} catch (e: IOException) {
			Startup.onSaveFail(e, target.toString())
			return false
		}
	}

	/** Opens the document's input stream, counting bytes when [progress] is set. */
	private fun openCounted(source: Uri, progress: Progress?): InputStream {
		val stream = GlobalDependencies.appContext.contentResolver.openInputStream(source)
			?: throw IOException("No stream for $source")
		return if (progress == null) stream else ProgressInputStream(stream, progress)
	}

	/** Opens the document's output stream, counting bytes when [progress] is set. */
	private fun openCountedOut(target: Uri, progress: Progress?): OutputStream {
		val stream = GlobalDependencies.appContext.contentResolver.openOutputStream(target, "wt")
			?: throw IOException("No stream for $target")
		return if (progress == null) stream else ProgressOutputStream(stream, progress)
	}

	/** Counts input bytes and reports each to [progress]. */
	private class ProgressInputStream(wrapped: InputStream, private val progress: Progress) :
		FilterInputStream(wrapped) {
		override fun read(): Int = super.read().also { if (it >= 0) progress.advance(1) }
		override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
			if (it > 0) progress.advance(it)
		}
	}

	/** Counts output bytes and reports each to [progress]. */
	private class ProgressOutputStream(wrapped: OutputStream, private val progress: Progress) :
		FilterOutputStream(wrapped) {
		// straight to out — FilterOutputStream's bulk write loops through the
		// single-byte write, which would count every byte twice
		override fun write(b: Int) {
			out.write(b)
			progress.advance(1)
		}
		override fun write(b: ByteArray, off: Int, len: Int) {
			out.write(b, off, len)
			progress.advance(len)
		}
	}
}
