package cz.litoj.schlmgr.db.io

import org.hjson.JsonArray
import org.hjson.JsonObject
import org.hjson.JsonValue
import org.hjson.ParseException
import org.hjson.Stringify
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository

/**
 * The plain parse/print result of a human-editable JSON (HJSON) file — no
 * database involved, so the parser stays testable and the caller decides where
 * the tree lands.
 *
 * The shape is recursive: a subject and every nested chapter look the same
 * (`name`, optional `desc`, the success counters `s`/`f`, `words`, `chapters`),
 * so [JsonChapter] carries its own children. A word adds `translations`.
 * The counter fields default to `0` and the description to `null`.
 */
class JsonTranslation(
	var name: String,
	var description: String? = null,
	var passed: Int = 0,
	var failed: Int = 0,
)

class JsonWord(
	var name: String,
	var description: String? = null,
	var passed: Int = 0,
	var failed: Int = 0,
) {
	val translations = ArrayList<JsonTranslation>()
}

class JsonChapter(
	var name: String,
	var description: String? = null,
	var passed: Int = 0,
	var failed: Int = 0,
) {
	val words = ArrayList<JsonWord>()
	val chapters = ArrayList<JsonChapter>()
}

/**
 * Reads and writes the tree JSON export — a human-editable HJSON file that,
 * unlike the `.txt` word list, also carries the success counters. The parser
 * accepts plain JSON too.
 *
 * The file's top level is one object (one exported container) or an array of
 * objects (several). The optional fields are omitted when unset, so the file
 * stays clean; a re-import fills them with the defaults.
 *
 * This class is the codec only: [render] pulls the tree from the database and
 * [parse] builds it from text, but no method touches the database on the
 * writing side — `ItemRepository.importJson` persists a parsed tree.
 */
object JsonWordList {

	/**
	 * Renders [containers] as HJSON: one object for a single container, an
	 * array for several. The children come from the database (words and their
	 * translations, nested chapters), notes are skipped.
	 */
	@JvmStatic
	fun render(containers: List<DbItem>): String = renderChapters(containers.map(::readChapter))

	/**
	 * Renders a hand-built tree with no database access — the round-trip path of
	 * [render]. One chapter gives an object, several an array.
	 */
	@JvmStatic
	fun renderChapters(chapters: List<JsonChapter>): String {
		val root: JsonValue = if (chapters.size == 1) chapterValue(chapters[0])
		else JsonArray().apply { chapters.forEach { add(chapterValue(it)) } }
		return root.toString(Stringify.HJSON)
	}

	/**
	 * Parses HJSON or plain JSON into the tree. Accepts one object or an array
	 * of objects and always returns a list.
	 *
	 * @throws IllegalArgumentException with a positioned message when the text
	 * is malformed or a value has the wrong type
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

	private fun readChapter(item: DbItem): JsonChapter {
		val chapter = JsonChapter(
			item.name, item.description.ifEmpty { null }, item.passedTests, item.failedTests,
		)
		for (child in ItemRepository.children(item.id)) when (child.type) {
			ItemKind.WORD -> chapter.words.add(readWord(child))
			ItemKind.CHAPTER -> chapter.chapters.add(readChapter(child))
			else -> {}
		}
		return chapter
	}

	private fun readWord(item: DbItem): JsonWord {
		val word = JsonWord(
			item.name, item.description.ifEmpty { null }, item.passedTests, item.failedTests,
		)
		for (trl in ItemRepository.translations(item.id)) {
			word.translations.add(
				JsonTranslation(
					trl.name, trl.description.ifEmpty { null }, trl.passedTests, trl.failedTests,
				)
			)
		}
		return word
	}

	private fun chapterValue(chapter: JsonChapter): JsonObject {
		val obj = JsonObject()
		obj.add("name", chapter.name)
		chapter.description?.takeIf { it.isNotEmpty() }?.let { obj.add("desc", it) }
		if (chapter.passed != 0) obj.add("s", chapter.passed)
		if (chapter.failed != 0) obj.add("f", chapter.failed)
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
		obj.add("name", word.name)
		word.description?.takeIf { it.isNotEmpty() }?.let { obj.add("desc", it) }
		if (word.passed != 0) obj.add("s", word.passed)
		if (word.failed != 0) obj.add("f", word.failed)
		if (word.translations.isNotEmpty()) {
			obj.add("translations", JsonArray().apply {
				word.translations.forEach { trl ->
					val t = JsonObject()
					t.add("name", trl.name)
					trl.description?.takeIf { it.isNotEmpty() }?.let { t.add("desc", it) }
					if (trl.passed != 0) t.add("s", trl.passed)
					if (trl.failed != 0) t.add("f", trl.failed)
					add(t)
				}
			})
		}
		return obj
	}

	// ------------------------------------------------------------ reading

	private fun parseChapter(value: JsonValue, path: String): JsonChapter {
		val obj = asObject(value, path)
		val name = optionalString(obj, "name", path)
			?: throw IllegalArgumentException("$path: missing 'name'")
		if (name.isEmpty()) throw IllegalArgumentException("$path: empty 'name'")
		val chapter = JsonChapter(
			name,
			optionalString(obj, "desc", path),
			optionalInt(obj, "s", path),
			optionalInt(obj, "f", path),
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
		val name = optionalString(obj, "name", path)
			?: throw IllegalArgumentException("$path: missing 'name'")
		if (name.isEmpty()) throw IllegalArgumentException("$path: empty 'name'")
		val word = JsonWord(
			name,
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

	/** The parser message without its own trailing "at line:col" suffix. */
	private fun ParseException.describe(): String {
		val reason = message?.substringBeforeLast(" at ") ?: "malformed input"
		return "JSON line $line, column $column: $reason"
	}
}
