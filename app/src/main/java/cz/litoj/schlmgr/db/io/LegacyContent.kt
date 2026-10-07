package cz.litoj.schlmgr.db.io

import java.util.LinkedList

/**
 * A node of the parsed old JSON format: a plain parameter map (values are
 * strings, numbers, booleans, nested [LegacyContent] or lists of them).
 *
 * This is the engine `ReadElement.Content`/`ContentReader` pair ported out
 * of the engine — it parses, it does not create objects. The legacy keys are
 * the old format's own: `class`, `name`, `s` (passed),
 * `f` (failed), `cdrn` (children), `desc`.
 */
class LegacyContent private constructor(val params: MutableMap<String, Any?>) {

	companion object {
		const val CLASS = "class"
		const val NAME = "name"
		const val SUCCESS = "s"
		const val FAIL = "f"
		const val CHILDREN = "cdrn"
		const val DESC = "desc"
	}

	/** The simple type name stored in the `class` parameter, or `null`. */
	fun typeName(): String? {
		val cls = params[CLASS] ?: return null
		val s = cls.toString()
		return s.substring(s.lastIndexOf('.') + 1)
	}

	fun name(): String {
		val n = params[NAME]
		return n?.toString() ?: ""
	}

	fun desc(): String {
		val d = params[DESC]
		return d?.toString() ?: ""
	}

	fun passed(): Int {
		return (params[SUCCESS] as? Number)?.toInt() ?: 0
	}

	fun failed(): Int {
		return (params[FAIL] as? Number)?.toInt() ?: 0
	}

	/** The child nodes; an empty list when the node has none. */
	@Suppress("UNCHECKED_CAST")
	fun children(): List<LegacyContent> {
		val out = params[CHILDREN]
		return if (out is List<*>) out as List<LegacyContent> else emptyList()
	}

	override fun toString(): String {
		return params.toString()
	}

	/**
	 * Resolves the old-format text into a parameter tree. The source is the
	 * engine's home-made JSON: `{"key":value,...}` objects, arrays of objects
	 * and plain scalars.
	 */
	class Reader(s: String) {

		private val str: String = s
		private var index: Int = -1
		/**
		 * The root node of the parsed tree.
		 */
		val mContent: LegacyContent = loadContent()

		/**
		 * The next character of the source. A source cut off mid-notation ends
		 * the scan with a positioned error instead of an index crash.
		 */
		private fun next(): Char {
			if (index + 1 >= str.length) throw IllegalArgumentException(
				"Source ended inside an unfinished entry, at index $index\n..." +
					str.substring(Math.max(index - 100, 0), index + 1) + "<-- this"
			)
			return str[++index]
		}

		private fun loadContent(): LegacyContent {
			val created = LegacyContent(HashMap())
			while (next() != '{') {
			}
			while (loadValue(created)) {
			}
			return created
		}

		/**
		 * Adds the next read value to the given node.
		 *
		 * @return `false` when the end of the current object notation was
		 * reached, `true` when a value was added
		 */
		private fun loadValue(content: LegacyContent): Boolean {
			var builder = StringBuilder()
			var ch: Char
			while (next().also { ch = it } != '"') {
				if (ch == '}') {
					return false
				}
			}
			while (next().also { ch = it } != '"') {
				builder.append(ch)
			}
			val key = builder.toString()
			while (next().also { ch = it } != '"' && !ch.isLetterOrDigit()) {
				if (ch == '{') { //found an object
					index--
					content.params[key] = loadContent()
					return true
				} else if (ch == '[') { //found an array
					val items = LinkedList<LegacyContent>()
					while (next().also { ch = it } != ']') {
						if (ch == '{') {
							index--
							items.add(loadContent())
						}
					}
					content.params[key] = items
					return true
				}
			}
			val string = ch == '"'
			builder = StringBuilder()
			if (string) {
				while (next().also { ch = it } != '"') {
					if (ch == '\\') {
						when (next().also { ch = it }) {
							'n' ->
								ch = '\n'
							't' ->
								ch = '\t'
						}
					}
					builder.append(ch)
				}
			} else {
				do {
					builder.append(ch)
				} while (next().also { ch = it }.isLetterOrDigit() || ch == '.')
				ch = str[--index]
			}
			try {
				if (string) {
					content.params[key] = builder.toString()
				} else if (!builder[0].isDigit()) {
					content.params[key] = builder.toString().toBoolean()
				} else if (ch == 'L') {
					content.params[key] = builder.toString().toLong()
				} else if (ch == 'f') {
					content.params[key] = builder.toString().toFloat()
				} else if (builder.indexOf(".") > -1 || builder.indexOf("E") > -1 &&
					builder[0] != '0') {
					content.params[key] = builder.toString().toDouble()
				} else {
					content.params[key] = builder.toString().toInt()
				}
			} catch (iae: IllegalArgumentException) {
				throw IllegalArgumentException("On field '" + key + "', before char: " +
					index + "\n..." + str.substring(Math.max(index - 100, 0), index) +
					"<-- this", iae)
			}
			return true
		}
	}
}
