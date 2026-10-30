package cz.litoj.schlmgr.data.json

import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import org.hjson.JsonArray
import org.hjson.JsonObject
import org.hjson.JsonValue
import org.hjson.ParseException
import org.hjson.Stringify
import cz.litoj.schlmgr.data.StreamBuilder

class JsonTranslation(
	var name: String,
	var description: String? = null,
	var passedTests: Int = 0,
	var failedTests: Int = 0,
)

sealed interface JsonChild

class JsonWord(
	var name: String,
	var description: String? = null,
	var passedTests: Int = 0,
	var failedTests: Int = 0,
) : JsonChild {
	val translations = ArrayList<JsonTranslation>()
}

class JsonNote(var name: String, var description: String? = null) : JsonChild

/**
 * The format carries no counters, since a container derives them from its words.
 */
class JsonChapter(
	var name: String,
	var description: String? = null,
) : JsonChild {
	val entries = ArrayList<JsonChild>()
}

/**
 * - an exported root name comes first, then the containers below it.
 * - the name of the target comes last, and the root alone names the root itself.
 * - the path resolves once every row of the import is saved.
 */
class JsonRef(val path: List<String>) : JsonChild

/**
 * The tree JSON export in HJSON form, human-editable.
 * - it carries the success counters that the txt word list drops.
 * - one node shape everywhere: children marks a chapter, translations a word.
 * - ref marks a reference entry, and neither key marks a note.
 * - the file is a list of entries landing in the import target.
 * - pure codec, no database, and the repository feeds it.
 */
object JsonWordList {

	/**
	 * - the caller owns the tree, and the exporter converts its stream into it.
	 * - tests build it by hand.
	 * - the file is a list of entries, so any root kind writes.
	 */
	@JvmStatic
	fun writeEntries(entries: List<JsonChild>): String {
		val root: JsonValue = if (entries.size == 1) childValue(entries[0])
		else JsonArray().apply { entries.forEach { add(childValue(it)) } }
		return root.toString(Stringify.HJSON)
	}

	/**
	 * - it always returns a list, so one object is a one-item array.
	 *
	 * @throws IllegalArgumentException when the text is malformed or a value has the wrong type
	 */
	@JvmStatic
	fun parse(text: String): List<JsonChild> {
		val root = try {
			JsonValue.readHjson(text)
		} catch (e: ParseException) {
			throw IllegalArgumentException(e.describe())
		}
		return when {
			root.isArray -> root.asArray().mapIndexed { i, value -> parseEntry(value, "[$i]") }
			root.isObject -> listOf(parseEntry(root, "root"))
			else -> throw IllegalArgumentException("JSON root: expected an object or an array")
		}
	}

	private fun chapterValue(chapter: JsonChapter): JsonObject {
		val obj = JsonObject()
		putCommon(obj, chapter.name, chapter.description)
		obj.add("children", JsonArray().apply { chapter.entries.forEach { add(childValue(it)) } })
		return obj
	}

	private fun childValue(child: JsonChild): JsonValue = when (child) {
		is JsonChapter -> chapterValue(child)
		is JsonWord -> wordValue(child)
		is JsonNote -> noteValue(child)
		is JsonRef -> refValue(child)
	}

	private fun wordValue(word: JsonWord): JsonObject {
		val obj = JsonObject()
		putCommon(obj, word.name, word.description, word.passedTests, word.failedTests)
		obj.add("translations", JsonArray().apply {
			word.translations.forEach { add(translationValue(it)) }
		})
		return obj
	}

	private fun noteValue(note: JsonNote): JsonObject {
		val obj = JsonObject()
		putCommon(obj, note.name, note.description)
		return obj
	}

	private fun translationValue(trl: JsonTranslation): JsonObject {
		val obj = JsonObject()
		putCommon(obj, trl.name, trl.description, trl.passedTests, trl.failedTests)
		return obj
	}

	private fun refValue(ref: JsonRef): JsonObject {
		val obj = JsonObject()
		obj.add("ref", JsonArray().apply { ref.path.forEach { add(it) } })
		return obj
	}

	/**
	 * - unset optionals are omitted, so the file stays clean.
	 * - a re-import fills them with the defaults.
	 */
	private fun putCommon(
		obj: JsonObject, name: String, description: String?,
		passedTests: Int = 0, failedTests: Int = 0,
	) {
		obj.add("name", name)
		description?.takeIf { it.isNotEmpty() }?.let { obj.add("desc", it) }
		if (passedTests != 0) obj.add("s", passedTests)
		if (failedTests != 0) obj.add("f", failedTests)
	}

	private fun parseEntry(value: JsonValue, path: String): JsonChild {
		val obj = asObject(value, path)
		// the retired save format, whose split keys lost the order and never
		// held notes, so a re-export from the database is the only fix
		if (hasKey(obj, "words") || hasKey(obj, "chapters"))
			throw IllegalArgumentException(
				"$path: 'words' and 'chapters' are the retired format's keys — re-export the file from the database")
		if (hasKey(obj, "ref")) return parseRef(obj, path)
		val hasChildren = hasKey(obj, "children")
		val hasTranslations = hasKey(obj, "translations")
		if (hasChildren && hasTranslations)
			throw IllegalArgumentException("$path: a node holds 'children' or 'translations', never both")
		return if (hasChildren) parseChapter(obj, path)
		else if (hasTranslations) parseWord(obj, path)
		else JsonNote(requireName(obj, path), optionalString(obj, "desc", path))
	}

	private fun parseRef(obj: JsonObject, path: String): JsonRef {
		for (key in listOf("name", "desc", "children", "translations"))
			if (hasKey(obj, key))
				throw IllegalArgumentException("$path: a reference entry holds only 'ref'")
		val value = obj.get("ref")!!
		if (!value.isArray) throw IllegalArgumentException("$path.ref: expected an array")
		val names = value.asArray().mapIndexed { i, element ->
			if (!element.isString) throw IllegalArgumentException("$path.ref[$i]: expected a string")
			element.asString()
		}
		if (names.isEmpty())
			throw IllegalArgumentException("$path.ref: a path needs at least the target")
		return JsonRef(names)
	}

	private fun parseChapter(obj: JsonObject, path: String): JsonChapter {
		val chapter = JsonChapter(
			requireName(obj, path),
			optionalString(obj, "desc", path),
		)
		optionalArray(obj, "children", path)?.forEachIndexed { i, child ->
			chapter.entries.add(parseEntry(child, "$path.children[$i]"))
		}
		return chapter
	}

	private fun parseWord(obj: JsonObject, path: String): JsonWord {
		val word = JsonWord(
			requireName(obj, path),
			optionalString(obj, "desc", path),
			optionalInt(obj, "s", path),
			optionalInt(obj, "f", path),
		)
		optionalArray(obj, "translations", path)?.forEachIndexed { i, child ->
			word.translations.add(parseTranslation(child, "$path.translations[$i]"))
		}
		return word
	}

	private fun parseTranslation(value: JsonValue, path: String): JsonTranslation {
		val obj = asObject(value, path)
		val name = optionalString(obj, "name", path)
			?: throw IllegalArgumentException("$path: missing 'name'")
		return JsonTranslation(
			name,
			optionalString(obj, "desc", path),
			optionalInt(obj, "s", path),
			optionalInt(obj, "f", path),
		)
	}

	/** Key presence under the null-means-missing rule of every optional. */
	private fun hasKey(obj: JsonObject, key: String): Boolean {
		val value = obj.get(key) ?: return false
		return !value.isNull
	}

	private fun requireName(obj: JsonObject, path: String): String {
		val name = optionalString(obj, "name", path)
			?: throw IllegalArgumentException("$path: missing 'name'")
		if (name.isEmpty()) throw IllegalArgumentException("$path: empty 'name'")
		return name
	}

	private fun asObject(value: JsonValue, path: String): JsonObject {
		if (!value.isObject) throw IllegalArgumentException("$path: expected an object")
		return value.asObject()
	}

	private fun optionalString(obj: JsonObject, key: String, path: String): String? {
		val value = obj.get(key) ?: return null
		if (value.isNull) return null
		if (!value.isString) throw IllegalArgumentException("$path.$key: expected a string")
		return value.asString()
	}

	private fun optionalInt(obj: JsonObject, key: String, path: String): Int {
		val value = obj.get(key) ?: return 0
		if (!value.isNumber) throw IllegalArgumentException("$path.$key: expected a number")
		return value.asInt()
	}

	private fun optionalArray(obj: JsonObject, key: String, path: String): JsonArray? {
		val value = obj.get(key) ?: return null
		if (value.isNull) return null
		if (!value.isArray) throw IllegalArgumentException("$path.$key: expected an array")
		return value.asArray()
	}
}

/** The parser message without its trailing "at line:col" suffix. */
internal fun ParseException.describe(): String {
	val reason = message?.substringBeforeLast(" at ") ?: "malformed input"
	return "JSON line $line, column $column: $reason"
}

/**
 * - the reference entries arrive as [StreamReference]s.
 * - see [DataImporter] for the stream contract.
 * - every top-level item lands in the import target as it is.
 * - a chapter becomes a subject at the root, and a word or note joins the open container rows.
 * - references resolve against saved rows after every item, so a path may name rows reached later.
 * - the stored counters of a container never enter the stream.
 * - only word and translation counters travel.
 */
class HJsonImporter(text: String) : DataImporter {
	override val items: List<StreamItem>
	override val references: List<StreamReference>

	init {
		val out = ArrayList<StreamItem>()
		val refs = ArrayList<StreamReference>()
		for (entry in JsonWordList.parse(text)) when (entry) {
			// a reference names a target for a parent chapter of the file,
			// since the top level itself is no parent
			is JsonRef -> throw IllegalArgumentException(
				"a reference needs a parent chapter in the file")
			is JsonChapter -> addChapter(out, refs, entry, null)
			is JsonWord -> addWord(out, entry, null)
			is JsonNote ->
				StreamBuilder.addItem(out, ItemKind.NOTE, entry.name, entry.description ?: "", null)
		}
		items = out
		references = refs
	}

	private fun addChapter(
		out: MutableList<StreamItem>, refs: MutableList<StreamReference>,
		chapter: JsonChapter, parent: StreamItem?,
	) {
		val ref = StreamBuilder.addChapter(out, chapter.name, chapter.description ?: "", parent)
		addEntries(out, refs, chapter.entries, ref)
	}

	private fun addEntries(
		out: MutableList<StreamItem>, refs: MutableList<StreamReference>,
		entries: List<JsonChild>, parent: StreamItem,
	) {
		for (entry in entries) when (entry) {
			is JsonRef -> refs.add(StreamReference(parent, entry.path.dropLast(1), entry.path.last()))
			is JsonWord -> addWord(out, entry, parent)
			is JsonNote ->
				StreamBuilder.addItem(out, ItemKind.NOTE, entry.name, entry.description ?: "", parent)
			is JsonChapter -> addChapter(out, refs, entry, parent)
		}
	}

	private fun addWord(out: MutableList<StreamItem>, word: JsonWord, parent: StreamItem?) {
		val ref = StreamBuilder.addWord(
			out, word.name, word.description ?: "", parent, word.passedTests, word.failedTests)
		for (trl in word.translations) StreamBuilder.addTranslation(
			out, trl.name, trl.description ?: "", ref, trl.passedTests, trl.failedTests)
	}
}

class HJsonExporter : DataExporter {
	override fun render(items: List<StreamItem>): String {
		val childrenOf = StreamBuilder.childrenOf(items)
		return JsonWordList.writeEntries(
			items.filter { it.parent == null }.map { entryOf(it, childrenOf) })
	}

	/** Every stream root writes as itself, a chapter, word or note alike. */
	private fun entryOf(item: StreamItem, childrenOf: Map<StreamItem, List<StreamItem>>): JsonChild =
		when (item.item.type) {
			ItemKind.CHAPTER -> chapterOf(item, childrenOf)
			ItemKind.WORD -> wordOf(item, childrenOf)
			else -> JsonNote(item.item.name, item.item.description.ifEmpty { null })
		}

	private fun chapterOf(item: StreamItem, childrenOf: Map<StreamItem, List<StreamItem>>): JsonChapter {
		val chapter = JsonChapter(item.item.name, item.item.description.ifEmpty { null })
		for (child in childrenOf[item] ?: emptyList()) when {
			// a reference names its target, and the plain home holds the row
			child.refPath != null -> chapter.entries.add(JsonRef(child.refPath))
			child.item.type == ItemKind.WORD -> chapter.entries.add(wordOf(child, childrenOf))
			child.item.type == ItemKind.CHAPTER -> chapter.entries.add(chapterOf(child, childrenOf))
			else -> chapter.entries.add(JsonNote(child.item.name, child.item.description.ifEmpty { null }))
		}
		return chapter
	}

	private fun wordOf(item: StreamItem, childrenOf: Map<StreamItem, List<StreamItem>>): JsonWord {
		val word = JsonWord(
			item.item.name, item.item.description.ifEmpty { null },
			item.item.passedTests, item.item.failedTests)
		for (trl in (childrenOf[item] ?: emptyList()).filter { it.item.type == ItemKind.TRANSLATION })
			word.translations.add(
				JsonTranslation(
					trl.item.name, trl.item.description.ifEmpty { null },
					trl.item.passedTests, trl.item.failedTests))
		return word
	}
}
