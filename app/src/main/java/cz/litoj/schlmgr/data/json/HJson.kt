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

class JsonWord(
	var name: String,
	var description: String? = null,
	var passedTests: Int = 0,
	var failedTests: Int = 0,
) {
	val translations = ArrayList<JsonTranslation>()
}

/** One chapter node of the tree JSON file, with nested words and chapters —
 * no counters, a container's are derived from its words, so the format
 * carries none. */
class JsonChapter(
	var name: String,
	var description: String? = null,
) {
	val words = ArrayList<JsonWord>()
	val chapters = ArrayList<JsonChapter>()
}

/**
 * The tree JSON export (HJSON) — human-editable, carries the success counters
 * the `.txt` word list drops.
 * - top level is one object or an array.
 * - pure codec — no database. The repository feeds it.
 */
object JsonWordList {

	/**
	 * Writes [chapters] as HJSON.
	 * - one object for a single chapter, an array for several.
	 * - the caller owns the tree — the exporter converts its stream into
	 *   it, tests build it by hand.
	 */
	@JvmStatic
	fun writeChapters(chapters: List<JsonChapter>): String {
		val root: JsonValue = if (chapters.size == 1) chapterValue(chapters[0])
		else JsonArray().apply { chapters.forEach { add(chapterValue(it)) } }
		return root.toString(Stringify.HJSON)
	}

	/**
	 * Parses HJSON or plain JSON into the tree. Accepts one object or an array of
	 * objects and always returns a list.
	 *
	 * @throws IllegalArgumentException when the text is malformed or a value has the wrong type
	 */
	@JvmStatic
	fun parse(text: String): List<JsonChapter> {
		val root = try {
			JsonValue.readHjson(text)
		} catch (e: ParseException) {
			throw IllegalArgumentException(e.describe())
		}
		return when {
			root.isArray -> root.asArray().mapIndexed { i, value -> parseChapter(value, "[$i]") }
			root.isObject -> listOf(parseChapter(root, "root"))
			else -> throw IllegalArgumentException("JSON root: expected an object or an array")
		}
	}

	// ------------------------------------------------------------ writing

	private fun chapterValue(chapter: JsonChapter): JsonObject {
		val obj = JsonObject()
		obj.add("name", chapter.name)
		chapter.description?.takeIf { it.isNotEmpty() }?.let { obj.add("desc", it) }
		if (chapter.words.isNotEmpty()) {
			obj.add("words", JsonArray().apply { chapter.words.forEach { add(wordValue(it)) } })
		}
		if (chapter.chapters.isNotEmpty()) {
			obj.add("chapters", JsonArray().apply { chapter.chapters.forEach { add(chapterValue(it)) } })
		}
		return obj
	}

	private fun wordValue(word: JsonWord): JsonObject {
		val obj = JsonObject()
		putCommon(obj, word.name, word.description, word.passedTests, word.failedTests)
		if (word.translations.isNotEmpty()) {
			obj.add("translations", JsonArray().apply {
				word.translations.forEach { add(translationValue(it)) }
			})
		}
		return obj
	}

	private fun translationValue(trl: JsonTranslation): JsonObject {
		val obj = JsonObject()
		putCommon(obj, trl.name, trl.description, trl.passedTests, trl.failedTests)
		return obj
	}

	/**
	 * The shared `name`/`desc`/`s`/`f` fields — unset optionals are omitted
	 * so the file stays clean. A re-import fills them with the defaults.
	 */
	private fun putCommon(obj: JsonObject, name: String, description: String?, passedTests: Int, failedTests: Int) {
		obj.add("name", name)
		description?.takeIf { it.isNotEmpty() }?.let { obj.add("desc", it) }
		if (passedTests != 0) obj.add("s", passedTests)
		if (failedTests != 0) obj.add("f", failedTests)
	}

	// ------------------------------------------------------------ reading

	private fun parseChapter(value: JsonValue, path: String): JsonChapter {
		val obj = asObject(value, path)
		val chapter = JsonChapter(
			requireName(obj, path),
			optionalString(obj, "desc", path),
		)
		optionalArray(obj, "words", path)?.forEachIndexed { i, child ->
			chapter.words.add(parseWord(child, "$path.words[$i]"))
		}
		optionalArray(obj, "chapters", path)?.forEachIndexed { i, child ->
			chapter.chapters.add(parseChapter(child, "$path.chapters[$i]"))
		}
		return chapter
	}

	private fun parseWord(value: JsonValue, path: String): JsonWord {
		val obj = asObject(value, path)
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

/** The parser message without its own trailing "at line:col" suffix. */
internal fun ParseException.describe(): String {
	val reason = message?.substringBeforeLast(" at ") ?: "malformed input"
	return "JSON line $line, column $column: $reason"
}

/**
 * The tree JSON format as an import source — the parsed tree as a
 * [StreamItem] stream (see [DataImporter]).
 * - each top-level subject of the file becomes a root item.
 * - a container's own stored counters never enter the stream (derived from
 *   the words below on every read) — only word/translation counters travel.
 */
class HJsonImporter(text: String) : DataImporter {
	override val items: List<StreamItem>
	override val references: List<StreamReference> = emptyList()

	init {
		val out = ArrayList<StreamItem>()
		for (subject in JsonWordList.parse(text)) addChapter(out, subject, null)
		items = out
	}

	private fun addChapter(out: MutableList<StreamItem>, chapter: JsonChapter, parent: StreamItem?) {
		val ref = StreamBuilder.addChapter(out, chapter.name, chapter.description ?: "", parent)
		for (word in chapter.words) addWord(out, word, ref)
		for (child in chapter.chapters) addChapter(out, child, ref)
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
		// the format's top level is chapters — a word or note root has no
		// node and is skipped, not converted
		return JsonWordList.writeChapters(
			items.filter { it.parent == null && it.item.type == ItemKind.CHAPTER }
				.map { chapterOf(it, childrenOf) })
	}

	private fun chapterOf(item: StreamItem, childrenOf: Map<StreamItem, List<StreamItem>>): JsonChapter {
		val chapter = JsonChapter(item.item.name, item.item.description.ifEmpty { null })
		for (child in childrenOf[item] ?: emptyList()) when (child.item.type) {
			ItemKind.WORD -> chapter.words.add(wordOf(child, childrenOf))
			ItemKind.CHAPTER -> chapter.chapters.add(chapterOf(child, childrenOf))
			// notes have no node in this format — skipped, not failed
			else -> {}
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
