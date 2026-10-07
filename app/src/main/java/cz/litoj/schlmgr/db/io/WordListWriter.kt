package cz.litoj.schlmgr.db.io

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository

/**
 * Renders word-list text (the former engine `SimpleWriter`) from a container
 * tree. The output syntax is the one [WordListReader] parses — the name
 * escapes the characters the reader treats as syntax, and the description
 * escapes its own end marker and escape char, so a rendered tree parses
 * back unchanged (see `WordListRoundTripTest`).
 */
object WordListWriter {

	@JvmStatic
	var wordSplitter: String = ";"
		private set
	private var wordSeparator: String = "\\"

	/**
	 * Sets the separator between a word and its translations. Any non-empty
	 * string; when it starts or ends with a space, the separator is widened
	 * accordingly.
	 */
	@JvmStatic
	fun setWordSplitter(splitter: String) {
		if (splitter.isEmpty()) return
		wordSplitter = splitter
		wordSeparator = if (splitter.first() == ' ' || splitter.last() == ' ') " \\ " else "\\"
	}

	/**
	 * Renders [containers]: each with the `Name { ... }` wrapper. The tree
	 * walk runs through the injectable lookups — the repository by default,
	 * a fake tree in the round-trip tests.
	 */
	@JvmStatic
	@JvmOverloads
	fun write(
		containers: List<DbItem>,
		children: (Int) -> List<DbItem> = ItemRepository::children,
		translations: (Int) -> List<DbItem> = ItemRepository::translations,
	): String {
		val sb = StringBuilder()
		for (container in containers) saveContent(sb, container, 0, children, translations)
		return sb.toString()
	}

	/** One container: its words and nested chapters, indented by [tabs]. */
	private fun saveContent(
		sb: StringBuilder,
		item: DbItem,
		tabs: Int,
		children: (Int) -> List<DbItem>,
		translations: (Int) -> List<DbItem>,
	) {
		indentation(sb, tabs)
		writeData(sb, item.name, item.description)
		sb.append(" {\n")
		for (child in children(item.id)) {
			when (child.type) {
				ItemKind.WORD -> {
					indentation(sb, tabs + 1)
					writeData(sb, child.name, child.description)
					sb.append(wordSplitter)
					var first = true
					for (trl in translations(child.id)) {
						if (first) first = false
						else sb.append(wordSeparator)
						writeData(sb, trl.name, trl.description)
					}
					sb.append('\n')
				}
				ItemKind.CHAPTER -> saveContent(sb, child, tabs + 1, children, translations)
				else -> {}
			}
		}
		indentation(sb, tabs)
		sb.append("}\n")
	}

	/**
	 * One name and its optional description. A `\` in a name is variant
	 * syntax, not a literal — it stays raw. The backslash in the description
	 * must be escaped first: the other escapes add backslashes of their own.
	 */
	private fun writeData(sb: StringBuilder, name: String, description: String?) {
		sb.append(
			name.replace(";", "\\;")
				.replace("=", "\\=")
				.replace("→", "\\→")
				.replace("[", "\\[")
				.replace("]", "\\]")
		)
		if (description != null && description.isNotEmpty()) {
			val desc = description.replace("\\", "\\\\").replace("]", "\\]")
			if (description.indexOf('\n') != -1) {
				sb.append(" [\n").append(desc).append('\n')
				sb.append(']')
			} else sb.append(" [").append(desc).append("]")
		}
	}

	private fun indentation(sb: StringBuilder, tabs: Int) {
		for (i in 0 until tabs) sb.append('\t')
	}
}
