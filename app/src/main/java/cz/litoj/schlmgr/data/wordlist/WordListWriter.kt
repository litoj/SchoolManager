package cz.litoj.schlmgr.data.wordlist

import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamBuilder

/**
 * Writes the word-list text from an item stream, in the syntax that [WordListReader] parses.
 * - chapter roots become a name and braces wrapper.
 * - a bare word or note root writes as its own top-level line.
 * - an interior backslash before an ordinary character has no encoding.
 * - a leading closing brace or a newline has no encoding.
 * - an odd backslash run before a separator gains one on first export, then stays stable.
 * - the reader trims every name and description.
 * - a lone CR normalizes to a newline, losing leading or trailing whitespace.
 * - a word or note with a blank name has no line, so the write refuses it.
 * - a reference link has no line, since its content writes at the plain home only.
 */
object WordListWriter {

	// written on main for the settings, read on export worker threads
	@Volatile
	@JvmStatic
	var wordTranslationSplitter: String = "="
		private set

	/**
	 * - the reader parses only those three.
	 * - anything else, empty included, keeps the current one.
	 */
	@JvmStatic
	fun setWordTranslationSplitter(splitter: String) {
		if (splitter != ";" && splitter != "=" && splitter != "→") return
		wordTranslationSplitter = splitter
	}

	@JvmStatic
	fun write(items: List<StreamItem>): String {
		val childrenOf = StreamBuilder.childrenOf(items)
		val sb = StringBuilder()
		for (root in items) if (root.parent == null) when (root.item.type) {
			ItemKind.CHAPTER -> saveContent(sb, root, 0, childrenOf)
			ItemKind.NOTE -> writeNoteLine(sb, root, 0)
			else -> writeWordLine(sb, root, childrenOf, 0)
		}
		return sb.toString()
	}

	private fun writeWordLine(
		sb: StringBuilder, word: StreamItem,
		childrenOf: Map<StreamItem, List<StreamItem>>, tabs: Int,
	) {
		// an empty name writes a bare splitter, and the reader would take the
		// line for a note, a silent kind change, or fail it outright
		require(word.item.name.isNotBlank()) { "A word without a name has no word-list line" }
		indentation(sb, tabs)
		writeData(sb, word.item.name, word.item.description)
		sb.append(wordTranslationSplitter)
		var first = true
		// only translations have a place on a word line, since the export walk
		// of the repository filters the same way
		for (trl in (childrenOf[word] ?: emptyList()).filter { it.item.type == ItemKind.TRANSLATION }) {
			if (first) first = false
			else sb.append('\\')
			writeData(sb, trl.item.name, trl.item.description)
		}
		sb.append('\n')
	}

	/** The separator leads because no word comes before it. */
	private fun writeNoteLine(sb: StringBuilder, note: StreamItem, tabs: Int) {
		// an empty name writes a bare splitter, and the reader fails the line
		require(note.item.name.isNotBlank()) { "A note without a name has no word-list line" }
		indentation(sb, tabs)
		sb.append(wordTranslationSplitter)
		writeData(sb, note.item.name, note.item.description, note = true)
		sb.append('\n')
	}

	private fun saveContent(
		sb: StringBuilder,
		item: StreamItem,
		tabs: Int,
		childrenOf: Map<StreamItem, List<StreamItem>>,
	) {
		indentation(sb, tabs)
		writeData(sb, item.item.name, item.item.description)
		sb.append(" {\n")
		for (child in childrenOf[item] ?: emptyList()) {
			// a reference has no line of its own, since its content sits at the
			// plain home, which the walk already wrote
			if (child.refPath != null) continue
			when (child.item.type) {
				ItemKind.WORD -> writeWordLine(sb, child, childrenOf, tabs + 1)
				ItemKind.CHAPTER -> saveContent(sb, child, tabs + 1, childrenOf)
				ItemKind.NOTE -> writeNoteLine(sb, child, tabs + 1)
				// nothing else has a line of its own in this format
				else -> {}
			}
		}
		indentation(sb, tabs)
		sb.append("}\n")
	}

	/**
	 * - a backslash in a word name is variant syntax, not a literal, so it stays raw.
	 * - a name-final backslash run doubles, and the reader halves it back.
	 * - a note name escapes every backslash, since notes carry no variant syntax.
	 */
	private fun writeData(sb: StringBuilder, name: String, description: String, note: Boolean = false) {
		var escaped = if (note) name.replace("\\", "\\\\") else evenOutBackslashRuns(name)
		escaped = escaped
			.replace(";", "\\;")
			.replace("=", "\\=")
			.replace("→", "\\→")
			.replace("[", "\\[")
			.replace("]", "\\]")
		if (!note) {
			var run = 0
			while (escaped.length > run && escaped[escaped.length - 1 - run] == '\\') run++
			repeat(run) { escaped += "\\" }
		}
		sb.append(escaped)
		if (description.isNotEmpty()) {
			val desc = description.replace("\\", "\\\\").replace("]", "\\]")
			if (description.indexOf('\n') != -1) {
				sb.append(" [\n").append(desc).append('\n')
				sb.append(']')
			} else sb.append(" [").append(desc).append(']')
		}
	}

	/**
	 * - an odd run would merge with the backslash of the escape and turn the separator live.
	 * - no exact odd encoding exists.
	 * - the written name gains one backslash and stays stable from then on.
	 */
	private fun evenOutBackslashRuns(name: String): String {
		val sb = StringBuilder(name.length)
		var i = 0
		while (i < name.length) {
			if (name[i] != '\\') {
				sb.append(name[i])
				i++
				continue
			}
			var run = 0
			while (name.getOrNull(i + run) == '\\') run++
			val after = name.getOrNull(i + run)
			val odd = run % 2 == 1 && after != null && ";=→[]".contains(after)
			repeat(if (odd) run + 1 else run) { sb.append('\\') }
			i += run
		}
		return sb.toString()
	}

	private fun indentation(sb: StringBuilder, tabs: Int) {
		for (i in 0 until tabs) sb.append('\t')
	}
}
