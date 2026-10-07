package cz.litoj.schlmgr.db.io

/**
 * The plain parse result of a word-list file — no database involved, so the
 * parser stays testable and the caller decides where the tree lands.
 */
class WordListEntry(var name: String, var description: String? = null)

class WordListWord(var name: String, var description: String? = null) {
	val translations = ArrayList<WordListEntry>()
}

class WordListChapter(val name: String, var description: String? = null) {
	val words = ArrayList<WordListWord>()
	val children = ArrayList<WordListChapter>()
}

/**
 * Parses the word-list text format (the former engine `SimpleReader`, ported
 * 1:1 minus the object creation — it builds [WordListChapter] trees instead).
 *
 * Used syntax:
 *  - chapter: `name of the chapter {` content `}`
 *  - word: `name\another name\more synonyms;translate\a synonym\...`
 *  - item description: `... [description, can contain more lines]`
 *
 * A `\` between words splits them: every word of one line shares the line's
 * translations. Throws [IllegalArgumentException] with a positioned message
 * on malformed input.
 */
class WordListReader private constructor(private val src: String, private val containerName: String) {

	val result = IntArray(3) // chapters, words, translations — the toast counters
	private var i = -1
	private var lineStart = 0
	private var line = 1

	companion object {

		/**
		 * Parses [source] as the content of a container named [containerName]
		 * (the name appears in error messages only).
		 *
		 * @return the parsed tree and the [result] counters
		 */
		@JvmStatic
		fun parse(source: String, containerName: String): Pair<WordListChapter, IntArray> {
			val reader = WordListReader(source, containerName)
			val root = WordListChapter(containerName)
			reader.loadContent(root.words, root.children)
			return Pair(root, reader.result)
		}
	}

	private fun loadContent(
		words: MutableList<WordListWord>,
		chapterChildren: MutableList<WordListChapter>,
	) {
		val sb = StringBuilder()
		val wordNames = ArrayList<String>()
		val wordDescs = ArrayList<String?>()
		val trlNames = ArrayList<String>()
		val trlDescs = ArrayList<String?>()
		var inWords = true
		while (++i < src.length) {
			when (val ch = src[i]) {
				'\\' -> {
					if (i + 1 < src.length) {
						i++
						val ch2 = src[i]
						when (ch2) {
							'\\', '/', '(', ')' -> sb.append('\\')
							';', '=', '→', '[', ']' -> {}
							else -> {
								// an unescaped `\` splits the current word/translation
								flush(sb, wordNames, wordDescs, trlNames, trlDescs, inWords)
								if (inWords) result[1]++ else result[2]++
							}
						}
						sb.append(ch2)
					}
				}
				';', '=', '→' -> {
					flush(sb, wordNames, wordDescs, trlNames, trlDescs, inWords)
					if (inWords && wordNames.isNotEmpty()) {
						result[1]++
						inWords = false
					} else throw report("Expected '[', '\\n' or text. Got '$ch'.")
				}
				'\r' -> {
					i++
					inWords = onNewline(sb, words, wordNames, wordDescs, trlNames, trlDescs, inWords)
				}
				'\n' -> inWords = onNewline(sb, words, wordNames, wordDescs, trlNames, trlDescs, inWords)
				'}' -> {
					if (src.substring(lineStart, i).trim().isEmpty()) {
						while (++i < src.length && src[i] != '\n');
						lineStart = i + 1
						line++
						return
					}
					sb.append(ch)
				}
				'{' -> {
					if (i + 1 < src.length && src[i + 1] == '\n' && inWords) {
						lineStart = ++i
						line++
						val name: String
						val desc: String?
						if (wordNames.isEmpty()) {
							if (sb.isNotEmpty() && sb[sb.length - 1] == '§') sb.setLength(sb.length - 1)
							name = sb.toString().trim()
							desc = null
						} else {
							name = wordNames[0]
							desc = wordDescs[0]
						}
						wordNames.clear(); wordDescs.clear()
						sb.setLength(0)
						val child = WordListChapter(name, desc)
						chapterChildren.add(child)
						loadContent(child.words, child.children)
						result[0]++
					} else sb.append(ch)
				}
				'[' -> {
					if (sb.isNotEmpty()) {
						flush(sb, wordNames, wordDescs, trlNames, trlDescs, inWords)
						val description = getDescription()
						if (inWords) wordDescs[wordDescs.size - 1] = description
						else trlDescs[trlDescs.size - 1] = description
					} else throw report("Expected text. Got '['.")
				}
				' ', '\t' -> if (sb.isNotEmpty()) sb.append(ch)
				else -> sb.append(ch)
			}
		}
		flush(sb, wordNames, wordDescs, trlNames, trlDescs, inWords)
		if (!inWords && trlNames.isNotEmpty()) {
			result[2]++
			wordNames.forEachIndexed { idx, w ->
				words.add(mkWord(w, wordDescs[idx], trlNames, trlDescs))
			}
		} else if (wordNames.isNotEmpty()) throw report(
			"Expected ';' and text. Reached end of file."
		)
	}

	/**
	 * The newline handler; returns the in-words state for the next line (a
	 * blank line before any word keeps skipping, like the legacy parser).
	 */
	private fun onNewline(
		sb: StringBuilder,
		words: MutableList<WordListWord>,
		wordNames: MutableList<String>, wordDescs: MutableList<String?>,
		trlNames: MutableList<String>, trlDescs: MutableList<String?>,
		inWords: Boolean,
	): Boolean {
		flush(sb, wordNames, wordDescs, trlNames, trlDescs, inWords)
		if (inWords && wordNames.isEmpty()) return true // blank line
		if (!inWords && trlNames.isNotEmpty()) {
			result[2]++
			wordNames.forEachIndexed { idx, w ->
				words.add(mkWord(w, wordDescs[idx], trlNames, trlDescs))
			}
			wordNames.clear(); wordDescs.clear()
			trlNames.clear(); trlDescs.clear()
			lineStart = i + 1
			line++
			return true
		}
		throw report("Expected ';' / '=' / '→' text, or '{'. Got '\\n'.")
	}

	/** Appends the pending [sb] text as one word or translation, if any. */
	private fun flush(
		sb: StringBuilder,
		wordNames: MutableList<String>, wordDescs: MutableList<String?>,
		trlNames: MutableList<String>, trlDescs: MutableList<String?>,
		inWords: Boolean,
	) {
		if (sb.isEmpty()) return
		if (inWords) {
			wordNames.add(sb.toString().trim())
			wordDescs.add(null)
		} else {
			trlNames.add(sb.toString().trim())
			trlDescs.add(null)
		}
		sb.setLength(0)
	}

	private fun mkWord(
		name: String, description: String?,
		trlNames: List<String>, trlDescs: List<String?>,
	): WordListWord {
		val w = WordListWord(name, description)
		for (j in trlNames.indices)
			w.translations.add(WordListEntry(trlNames[j], trlDescs[j]))
		return w
	}

	private fun getDescription(): String {
		val sb = StringBuilder()
		while (++i < src.length) {
			when (val ch = src[i]) {
				'\\' -> {
					if (i + 1 >= src.length) throw report("Expected ']' (end of description section). Reached end of file.")
					sb.append(src[++i])
				}
				']' -> return sb.toString().trim()
				// a newline inside the description is description content; the
				// writer's decorative [\n … \n] wrapper trims away
				'\n' -> {
					line++
					sb.append(ch)
				}
				else -> sb.append(ch)
			}
		}
		throw report("Expected ']' (end of description section). Reached end of file.")
	}

	private fun report(info: String): IllegalArgumentException =
		IllegalArgumentException(
			"$containerName - line $line:\n'${src.substring(lineStart, minOf(i + 1, src.length))}'\n$info"
		)
}
