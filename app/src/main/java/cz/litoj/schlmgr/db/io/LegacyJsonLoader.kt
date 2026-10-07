package cz.litoj.schlmgr.db.io

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.app.GlobalDependencies
import org.hjson.JsonObject
import org.hjson.JsonValue
import org.hjson.ParseException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * One normalized node of a parsed old-format subject — what to insert, with the
 * children already in their insert form (a word's children are its translations).
 * A chapter's counters stay zero: they are derived from the words below it, as
 * every read recomputes them.
 *
 * A node with [reference] set inserts no row at all: it is one of the old
 * `Reference` links, resolved to its target once every row exists.
 */
data class LegacyNode(
	val kind: ItemKind,
	val name: String,
	val desc: String,
	val passed: Int,
	val failed: Int,
	val children: List<LegacyNode> = emptyList(),
	val reference: LegacyRef? = null,
)

/**
 * A parsed legacy `Reference`: the referenced item's [name] and its [origin] —
 * the chain of container names from the subject (inclusive) to the item's parent
 * (exclusive), exactly as the old object stored it.
 */
class LegacyRef(val name: String, val origin: List<String>)

/**
 * Reports the bytes that have flowed through one long file operation — the bridge
 * between the I/O layer's counting stream wrappers ([LegacyJsonLoader.readText],
 * [LegacyJsonLoader.writeText]) and whatever shows the progress (the
 * [cz.litoj.schlmgr.app.FileProgress] notification).
 */
fun interface Progress {
	fun advance(bytes: Int)
}

/**
 * The old-format subject import and the app's SAF text I/O (the read/write the
 * imports and exports share).
 *
 * The old format is a normal chapter/word tree; a save chapter simply splits its
 * subtree into a separate `Chapters/name[hash].json` file. The node in the tree
 * only marks where the chapter sits — its description and content live in the
 * file, and a file can hold more save chapters, so the tree assembles from
 * wherever the format split it.
 *
 * Per the approved simplification, this loader parses the JSON (plain JSON is
 * valid HJSON, so the app's library reads it) onto [LegacyNode]s and hands them
 * to [ItemRepository.importLegacySubject], which inserts the whole subject in
 * one transaction. It never touches any other model. The old files stay on
 * disk untouched.
 *
 * Types are resolved by the simple class name (the qualifier changed with
 * the packages): `MainChapter`/`SaveChapter`/plain chapter →
 * `chapter`, `Word` → `word` (+ translation children), other
 * known leaves → `note`. A `Reference` node becomes a reference link to the
 * item its `origin` path names. Unknown types are skipped.
 */
object LegacyJsonLoader {

	/**
	 * Parses one old-format subject directory and inserts it through
	 * [ItemRepository.importLegacySubject] — one transaction for the whole subject.
	 *
	 * Every file is parsed before anything is written, so a malformed file fails
	 * the import with the database untouched. The bytes read are reported to
	 * [progress] (the total is [subjectSize]).
	 *
	 * @param dir the subject directory (containing `main.json`)
	 * @return the row id of the inserted subject root item, or -1 on failure
	 */
	@JvmStatic
	@JvmOverloads
	@Throws(Exception::class)
	fun insertSubjectDir(dir: DocumentFile, progress: Progress? = null): Int {
		// findFile, never a creating lookup: a missing file must not be born here
		val mainJson = dir.findFile("main.json") ?: return -1
		// the read reports its own failure — skipping the subject is enough
		if (!hasContent(mainJson)) return -1
		val root = parse(readText(mainJson, progress) ?: return -1)

		val subjectName = dir.name ?: return -1
		return ItemRepository.importLegacySubject(
			subjectName, root.string("desc"),
			children(root, dir.findFile("Chapters"), progress))
	}

	/**
	 * The bytes [insertSubjectDir] reads from [dir] — the total a progress bar
	 * over the import runs against. The files of the save chapters the tree
	 * actually reaches are a part of it, so the total is an upper bound.
	 */
	@JvmStatic
	fun subjectSize(dir: DocumentFile): Long {
		var total = dir.findFile("main.json")?.length() ?: return 0
		val chaptersDir = dir.findFile("Chapters") ?: return total
		for (file in chaptersDir.listFiles())
			if (file.name?.endsWith(".json") == true) total += file.length()
		return total
	}

	/**
	 * Maps one parsed node onto its insert form. A word's children become its
	 * translations; unknown types are skipped. A `Reference` node carries no row —
	 * the repository turns it into a reference link once the target exists.
	 */
	private fun nodeOf(
		value: JsonValue, chaptersDir: DocumentFile?, progress: Progress?,
	): LegacyNode? {
		if (!value.isObject) return null
		val obj = value.asObject()
		return when (obj.get("class")?.asString()?.substringAfterLast('.')) {
			"Word" -> LegacyNode(
				ItemKind.WORD, obj.string("name"), obj.string("desc"), obj.int("s"), obj.int("f"),
				obj.get("cdrn")?.takeIf { it.isArray }?.asArray()
					?.mapNotNull { translation(it) } ?: emptyList())
			"Chapter" -> LegacyNode(
				ItemKind.CHAPTER, obj.string("name"), obj.string("desc"), 0, 0,
				children(obj, chaptersDir, progress))
			"SaveChapter" -> saveChapter(obj, chaptersDir, progress)
			"Note" -> LegacyNode(
				ItemKind.NOTE, obj.string("name"), obj.string("desc"), obj.int("s"), obj.int("f"))
			// a persisted reference: no row, a link to the item at the origin path
			"Reference" -> (obj.get("origin")?.takeIf { it.isString }?.asString())?.let { origin ->
				LegacyNode(ItemKind.CHAPTER, obj.string("name"), "", 0, 0,
					reference = LegacyRef(obj.string("name"), origin.split("×")))
			}
			else -> null
		}
	}

	/** One translation of a parsed word — the word's children in the old format. */
	private fun translation(value: JsonValue): LegacyNode? {
		if (!value.isObject) return null
		val obj = value.asObject()
		return LegacyNode(
			ItemKind.TRANSLATION, obj.string("name"), obj.string("desc"),
			obj.int("s"), obj.int("f"))
	}

	/**
	 * A save chapter's node only marks where in the hierarchy the chapter sits:
	 * its description and content live in `Chapters/name[hash].json`, whose root
	 * is the chapter itself — and that file can hold more save chapters. A file
	 * that is gone leaves nothing to import.
	 */
	private fun saveChapter(
		obj: JsonObject, chaptersDir: DocumentFile?, progress: Progress?,
	): LegacyNode? {
		val dir = chaptersDir ?: return null
		val name = obj.string("name")
		if (name.isEmpty()) return null
		val hash = obj.int("hash").coerceAtLeast(1)
		val file = dir.findFile(if (hash == 1) "$name.json" else "$name[$hash].json")
		if (file == null || !hasContent(file)) return null
		val content = parse(readText(file, progress) ?: return null)
		return LegacyNode(
			ItemKind.CHAPTER, content.string("name").ifEmpty { name }, content.string("desc"),
			0, 0, children(content, chaptersDir, progress))
	}

	/** The `cdrn` children of one parsed node, in their insert form. */
	private fun children(
		obj: JsonObject, chaptersDir: DocumentFile?, progress: Progress?,
	): List<LegacyNode> {
		val cdrn = obj.get("cdrn")?.takeIf { it.isArray }?.asArray() ?: return emptyList()
		return cdrn.mapNotNull { nodeOf(it, chaptersDir, progress) }
	}

	/** Parses one old-format file — plain JSON is valid HJSON, so the library reads it. */
	private fun parse(text: String): JsonObject {
		val root = try {
			JsonValue.readHjson(text)
		} catch (e: ParseException) {
			throw IllegalArgumentException(e.describe())
		}
		if (!root.isObject) throw IllegalArgumentException("Expected a JSON object")
		return root.asObject()
	}

	/** The string at [key], "" when the node carries none. */
	private fun JsonObject.string(key: String): String =
		get(key)?.takeIf { it.isString }?.asString() ?: ""

	/** The number at [key], 0 when the node carries none. */
	private fun JsonObject.int(key: String): Int = get(key)?.takeIf { it.isNumber }?.asInt() ?: 0

	// ---------------------------------------------------------------- document text I/O

	/**
	 * Reads the whole document as UTF-8 text — the file read shared by the
	 * imports and the migration. A failure is reported through
	 * [Startup.onLoadFail] and `null` returned; callers skip the document.
	 * The bytes that flow through are reported to [progress] — a read of a
	 * large document is itself the bulk of the import.
	 */
	@JvmStatic
	@JvmOverloads
	fun readText(source: Uri, progress: Progress? = null): String? {
		try {
			val stream = GlobalDependencies.appContext.contentResolver.openInputStream(source)
				?: throw IOException("No stream for $source")
			val counted = if (progress == null) stream else ProgressInputStream(stream, progress)
			val sb = StringBuilder(4096)
			InputStreamReader(counted, StandardCharsets.UTF_8).use { isr ->
				val buffer = CharArray(1024)
				var amount: Int
				while (isr.read(buffer).also { amount = it } != -1) {
					sb.append(buffer, 0, amount)
				}
			}
			return sb.toString()
		} catch (e: IOException) {
			Startup.onLoadFail(e, source.toString())
			return null
		}
	}

	private fun readText(file: DocumentFile, progress: Progress? = null) = readText(file.uri, progress)

	/**
	 * Writes text as UTF-8 into the document — the file write shared by the
	 * exports. Returns whether the write succeeded; a failure is reported
	 * through [Startup.onSaveFail], so the caller can skip its success report.
	 * The bytes that flow out are reported to [progress].
	 */
	@JvmStatic
	@JvmOverloads
	fun writeText(target: Uri, content: String, append: Boolean = false, progress: Progress? = null): Boolean {
		try {
			val stream = GlobalDependencies.appContext.contentResolver.openOutputStream(
				target, if (append) "wa" else "wt")
				?: throw IOException("No stream for $target")
			val counted = if (progress == null) stream else ProgressOutputStream(stream, progress)
			OutputStreamWriter(counted, StandardCharsets.UTF_8).use { osw ->
				osw.write(content)
			}
			return true
		} catch (e: IOException) {
			Startup.onSaveFail(e, target.toString())
			return false
		}
	}

	/** Wraps the stream so the bytes that flow through it advance [progress]. */
	private class ProgressInputStream(wrapped: InputStream, private val progress: Progress) :
		FilterInputStream(wrapped) {
		override fun read(): Int = super.read().also { if (it >= 0) progress.advance(1) }
		override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
			if (it > 0) progress.advance(it)
		}
	}

	/** Wraps the stream so the bytes that flow out of it advance [progress]. */
	private class ProgressOutputStream(wrapped: OutputStream, private val progress: Progress) :
		FilterOutputStream(wrapped) {
		override fun write(b: ByteArray, off: Int, len: Int) {
			super.write(b, off, len)
			progress.advance(len)
		}
	}

	/** Whether [file] holds anything: a folder, or a non-empty file. */
	private fun hasContent(file: DocumentFile): Boolean =
		file.exists() && (file.isDirectory || file.length() > 0)
}
