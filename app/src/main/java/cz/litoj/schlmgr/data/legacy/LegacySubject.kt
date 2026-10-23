package cz.litoj.schlmgr.data.legacy

import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.Progress
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import org.hjson.JsonObject
import org.hjson.JsonValue
import org.hjson.ParseException
import cz.litoj.schlmgr.data.StreamBuilder
import cz.litoj.schlmgr.data.json.describe

/**
 * - the children of a word are its translations.
 * - chapter counters stay zero, since every read recomputes them.
 * - a node with [reference] set inserts no row, since it becomes a reference link.
 */
data class LegacyNode(
	val kind: ItemKind,
	val name: String,
	val description: String,
	val passedTests: Int,
	val failedTests: Int,
	val children: List<LegacyNode> = emptyList(),
	val reference: LegacyRef? = null,
)

/**
 * The path starts at the subject and runs down to the parent of the item.
 */
class LegacyRef(val name: String, val origin: List<String>)

/**
 * Where the files of one old-format subject come from.
 * - the gateway hands the texts over, such as main.json and Chapters/name.json.
 * - the loader parses pure strings with no platform I/O of its own.
 * - the gateway counts the bytes for the progress bar through FileText.
 * - nothing is recounted here.
 */
fun interface LegacyFileSource {
	/** The text of one file, or null when it is missing, empty or unreadable. */
	fun read(relativePath: String): String?
}

/** The file source the recursive load reads save-chapter files through, on demand. */
data class LoadContext(val source: LegacyFileSource)

/**
 * Old-format subject import from split files per save chapter.
 * - the types come from the simple class name, and the root MainChapter is the subject itself.
 * - reference links travel via origin, and unknown types are skipped.
 */
object LegacyJsonLoader {
	class LegacySubject(val name: String, val description: String, val nodes: List<LegacyNode>)

	/**
	 * Whether [dir] carries the two files that every old-format subject directory has.
	 * This is the pre-check of the directory picker.
	 */
	@JvmStatic
	fun isSubjectDir(dir: DocumentFile): Boolean =
		// findFile, never a creating lookup, since a missing file must not be born here
		dir.findFile("main.json") != null && dir.findFile("setts.dat") != null

	/**
	 * Parses one old-format subject from its handed texts.
	 * - every file parses before anything returns.
	 * - a malformed file fails with nothing persisted.
	 * - chapter files read on demand through [source], and [subjectSize] is the upper bound.
	 * - returns null when main.json is missing or empty.
	 */
	@JvmStatic
	@Throws(Exception::class)
	fun parseSubjectDir(source: LegacyFileSource, subjectName: String): LegacySubject? {
		val mainText = source.read("main.json")
		if (mainText.isNullOrEmpty()) return null
		val root = parse(mainText)

		val ctx = LoadContext(source)
		return LegacySubject(
			subjectName, root.string("desc"),
			children(root, ctx))
	}

	/**
	 * The bytes that [parseSubjectDir] reads from [dir], the total of the progress bar.
	 * - every .json of Chapters counts, reached or not.
	 * - the total is an upper bound.
	 */
	@JvmStatic
	fun subjectSize(dir: DocumentFile): Long {
		var total = dir.findFile("main.json")?.length() ?: return 0
		val chaptersDir = dir.findFile("Chapters") ?: return total
		for (file in chaptersDir.listFiles())
			if (file.name?.endsWith(".json") == true) total += file.length()
		return total
	}

	private fun nodeOf(value: JsonValue, ctx: LoadContext): LegacyNode? {
		if (!value.isObject) return null
		val obj = value.asObject()
		return when (obj.get("class")?.asString()?.substringAfterLast('.')) {
			// a word without translations is a note, since the model has no
			// translation-less word, and the counters die with the word role
			"Word" -> {
				val trls = obj.get("cdrn")?.takeIf { it.isArray }?.asArray()
					?.mapNotNull { translation(it) } ?: emptyList()
				LegacyNode(
					if (trls.isEmpty()) ItemKind.NOTE else ItemKind.WORD,
					obj.string("name"), obj.string("desc"),
					if (trls.isEmpty()) 0 else obj.int("s"),
					if (trls.isEmpty()) 0 else obj.int("f"), trls)
			}
			"Chapter" -> LegacyNode(
				ItemKind.CHAPTER, obj.string("name"), obj.string("desc"), 0, 0,
				children(obj, ctx))
			"SaveChapter" -> saveChapter(obj, ctx)
			// a note is never tested, so the file counters are dead data, not carried
			"Note" -> LegacyNode(
				ItemKind.NOTE, obj.string("name"), obj.string("desc"), 0, 0)
			// a persisted reference, so no row, just a link to the item at the origin path.
			// the old format uses × as the path separator
			"Reference" -> (obj.get("origin")?.takeIf { it.isString }?.asString())?.let { origin ->
				LegacyNode(ItemKind.CHAPTER, obj.string("name"), "", 0, 0,
					reference = LegacyRef(obj.string("name"), origin.split("×")))
			}
			// unknown types are skipped, not failed, since the old app stored
			// helper nodes this import does not use
			else -> null
		}
	}

	/** One translation of a parsed word, a bare node that carries no class. */
	private fun translation(value: JsonValue): LegacyNode? {
		if (!value.isObject) return null
		val obj = value.asObject()
		// a typed node under a Word is not a translation, so a note, a nested
		// chapter or an unknown type is skipped like unknown chapter children
		val kind = obj.get("class")?.takeIf { it.isString }?.asString()?.substringAfterLast('.')
		if (kind != null && kind != "Word" && kind != "Translation") return null
		if (obj.get("cdrn")?.takeIf { it.isArray } != null) return null
		return LegacyNode(
			ItemKind.TRANSLATION, obj.string("name"), obj.string("desc"),
			obj.int("s"), obj.int("f"))
	}

	/**
	 * A save chapter node only marks where in the hierarchy the chapter sits.
	 * - description and content live in Chapters/name[hash].json, and the root is the chapter.
	 * - a gone file leaves nothing to import.
	 * - the first save file omits the suffix, so a hash of 1 reads name.json.
	 */
	private fun saveChapter(obj: JsonObject, ctx: LoadContext): LegacyNode? {
		val name = obj.string("name")
		if (name.isEmpty()) return null
		val hash = obj.int("hash").coerceAtLeast(1)
		val text = ctx.source.read(
			if (hash == 1) "Chapters/$name.json" else "Chapters/$name[$hash].json")
		if (text.isNullOrEmpty()) return null
		val content = parse(text)
		return LegacyNode(
			ItemKind.CHAPTER, content.string("name").ifEmpty { name }, content.string("desc"),
			0, 0, children(content, ctx))
	}

	private fun children(obj: JsonObject, ctx: LoadContext): List<LegacyNode> {
		val cdrn = obj.get("cdrn")?.takeIf { it.isArray }?.asArray() ?: return emptyList()
		return cdrn.mapNotNull { nodeOf(it, ctx) }
	}

	/** Parses one old-format file, and since plain JSON is valid HJSON the library reads it. */
	private fun parse(text: String): JsonObject {
		val root = try {
			JsonValue.readHjson(text)
		} catch (e: ParseException) {
			throw IllegalArgumentException(e.describe())
		}
		if (!root.isObject) throw IllegalArgumentException("Expected a JSON object")
		return root.asObject()
	}

	private fun JsonObject.string(key: String): String =
		get(key)?.takeIf { it.isString }?.asString() ?: ""

	private fun JsonObject.int(key: String): Int = get(key)?.takeIf { it.isNumber }?.asInt() ?: 0
}

/**
 * - the old Reference links arrive as [StreamReference]s.
 * - they resolve against the saved rows, see [DataImporter].
 * - the subject row itself is the root item of the stream.
 */
class LegacySubjectImporter(subject: LegacyJsonLoader.LegacySubject) : DataImporter {
	override val items: List<StreamItem>
	override val references: List<StreamReference>

	init {
		val out = ArrayList<StreamItem>()
		val refs = ArrayList<StreamReference>()
		val root = StreamBuilder.addChapter(out, subject.name, subject.description, null)
		for (node in subject.nodes) addNode(out, refs, node, root)
		items = out
		references = refs
	}

	private fun addNode(
		out: MutableList<StreamItem>,
		refs: MutableList<StreamReference>,
		node: LegacyNode,
		parent: StreamItem,
	) {
		val ref = node.reference
		if (ref != null) {
			// the target may sit anywhere in the subject, so it resolves once saved
			refs.add(StreamReference(parent, ref.origin, ref.name))
			return
		}
		val item = StreamBuilder.addItem(
			out, node.kind, node.name, node.description, parent, node.passedTests, node.failedTests)
		for (child in node.children) addNode(out, refs, child, item)
	}
}
