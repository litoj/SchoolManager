package cz.litoj.schlmgr.data.wordlist

import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import cz.litoj.schlmgr.data.StreamBuilder

// The plain parse tree of a word-list file — no database involved, so the
// parser stays testable and the caller decides where the tree lands. A
// chapter holds words, notes and sub-chapters, a word holds translation
// entries, a note is one named entry.
class WordListEntry(var name: String, var description: String? = null)

sealed interface WordListChild

class WordListWord(var name: String, var description: String? = null) : WordListChild {
	val translations = ArrayList<WordListEntry>()
}

class WordListNote(var name: String, var description: String? = null) : WordListChild

class WordListChapter(val name: String, var description: String? = null) : WordListChild {
	val entries = ArrayList<WordListChild>()

	val words: List<WordListWord> get() = entries.filterIsInstance<WordListWord>()
	val notes: List<WordListNote> get() = entries.filterIsInstance<WordListNote>()
	val children: List<WordListChapter> get() = entries.filterIsInstance<WordListChapter>()
}

/**
 * The mutable line assembly of one chapter's [loadContent] — the pending
 * text plus the words/translations collected since the last newline,
 * which side of the separator the parser is on, and whether the line
 * is a note.
 */
private class ParserContext(var inWords: Boolean = true) {
	var inNote = false
	val sb = StringBuilder()
	val wordNames = ArrayList<String>()
	val wordDescs = ArrayList<String?>()
	val trlNames = ArrayList<String>()
	val trlDescs = ArrayList<String?>()
}

/**
 * The word-list text format:
 * - `name {` content `}` chapters.
 * - `name;translation` words (`;`, `=`, `→` all separate).
 * - `... [description]` descriptions.
 * - a separator-led line (`= name`) is a note — a word line without the word.
 */
class WordListReader private constructor(private val src: String, private val containerName: String) {

	private var i = -1
	private var lineStart = 0
	private var line = 1

	companion object {

		/**
		 * Parses the content of one container. `containerName` labels the
		 * parse errors — the import names the target the content lands in.
		 *
		 * @throws IllegalArgumentException on malformed input, with a positioned message
		 */
		@JvmStatic
		fun parse(source: String, containerName: String): WordListChapter {
			val reader = WordListReader(source, containerName)
			val root = WordListChapter(containerName)
			reader.loadContent(root.entries, isRootLoad = true)
			return root
		}
	}

	private fun loadContent(
		entries: MutableList<WordListChild>,
		isRootLoad: Boolean = false,
	) {
		val ctx = ParserContext()
		val sb = ctx.sb
		val wordNames = ctx.wordNames
		val wordDescs = ctx.wordDescs
		val trlNames = ctx.trlNames
		val trlDescs = ctx.trlDescs
		while (++i < src.length) {
			when (val ch = src[i]) {
				'\\' -> {
					if (i + 1 < src.length) {
						i++
						val ch2 = src[i]
						when (ch2) {
							// a note line unescapes its backslash — notes carry no
							// variant syntax to keep raw
							'\\', '/', '(', ')' -> if (!ctx.inNote) sb.append('\\')
							';', '=', '→', '[', ']' -> {}
							else -> {
								if (ctx.inNote) throw parseError(
							"A note has no word divider — an unescaped '\\' splits words only.")
								// an unescaped `\` splits the current word/translation
								flush(ctx)
							}
						}
						sb.append(ch2)
					}
				}
				';', '=', '→' -> {
					// a leading separator opens a note line — no word precedes it
					if (!ctx.inNote && ctx.inWords && wordNames.isEmpty() && sb.isEmpty()) {
						ctx.inNote = true
					} else {
						if (ctx.inNote) throw parseError("A note line holds no second separator.")
						flush(ctx)
						if (ctx.inWords && wordNames.isNotEmpty()) {
							ctx.inWords = false
						} else throw parseError("Expected '[', '\\n' or text. Got '$ch'.")
					}
				}
				'\r' -> {
					// a lone `\r` is a line ending too — only the CRLF pair consumes the
					// following `\n`
					if (src.getOrNull(i + 1) == '\n') i++
					onNewline(ctx, entries)
				}
				'\n' -> onNewline(ctx, entries)
				'}' -> {
					if (src.substring(lineStart, i).trim().isEmpty()) {
						// the root load has no chapter to close — a stray
						// brace must not swallow the rest of the file
						if (isRootLoad) throw parseError("Unexpected '}'. No chapter is open.")
						// skip to the next line ending — `\n` or a lone `\r`
						while (++i < src.length && src[i] != '\n' && src[i] != '\r');
						if (i < src.length && src[i] == '\r' && src.getOrNull(i + 1) == '\n') i++
						lineStart = i + 1
						line++
						return
					}
					sb.append(ch)
				}
				'{' -> {
					// the opener's line ending — `\n`, or `\r` (a lone CR, or the
					// head of a CRLF pair)
					val breakAfterBrace = when (src.getOrNull(i + 1)) {
						'\n', '\r' -> true
						else -> false
					}
					if (breakAfterBrace && ctx.inWords && !ctx.inNote) {
						if (src.getOrNull(i + 1) == '\r') {
							i++
							if (src.getOrNull(i + 1) == '\n') i++
						} else i++
						lineStart = i + 1
						line++
						val name: String
						val desc: String?
						if (wordNames.isEmpty()) {
							// a `§` before a chapter opener is an empty-name marker — it is
							// stripped, not shown
							if (sb.isNotEmpty() && sb[sb.length - 1] == '§') sb.setLength(sb.length - 1)
							name = halveTrailingBackslashes(sb.toString().trim())
							desc = null
						} else {
							// a `\`-divided name cannot name a chapter — the
							// divider words would silently vanish (a lone divider
							// leaves its tail text in sb)
							if (wordNames.size > 1 || sb.isNotEmpty()) throw parseError(
								"A chapter name cannot contain a word divider ('\\').")
							name = wordNames[0]
							desc = wordDescs[0]
						}
						wordNames.clear(); wordDescs.clear()
						sb.setLength(0)
						val child = WordListChapter(name, desc)
						entries.add(child)
						loadContent(child.entries)
					} else sb.append(ch)
				}
				'[' -> {
					// a whitespace-only piece is no text either — a blank name
					// would poison the writers' refusals
					if (sb.isNotBlank()) {
						flush(ctx)
						val description = getDescription()
						if (ctx.inWords) wordDescs[wordDescs.size - 1] = description
						else trlDescs[trlDescs.size - 1] = description
					} else throw parseError("Expected text. Got '['.")
				}
				' ', '\t' -> if (sb.isNotEmpty()) sb.append(ch)
				else -> sb.append(ch)
			}
		}
		flush(ctx)
		if (ctx.inNote) entries.add(emitNote(ctx))
		else if (!ctx.inWords && trlNames.isNotEmpty()) {
			wordNames.forEachIndexed { idx, w ->
				entries.add(mkWord(w, wordDescs[idx], trlNames, trlDescs))
			}
		} else if (wordNames.isNotEmpty()) throw parseError(
			"Expected ';' and text. Reached end of file."
		)
	}

	/**
	 * Ends the current line — a note line emits its note, a word line its
	 * words, a blank line only advances the position.
	 */
	private fun onNewline(ctx: ParserContext, entries: MutableList<WordListChild>) {
		flush(ctx)
		if (ctx.inNote) entries.add(emitNote(ctx))
		else if (ctx.inWords && ctx.wordNames.isEmpty()) {
			// blank line — nothing emits, the error positions still advance
		} else if (!ctx.inWords && ctx.trlNames.isNotEmpty()) {
			ctx.wordNames.forEachIndexed { idx, w ->
				entries.add(mkWord(w, ctx.wordDescs[idx], ctx.trlNames, ctx.trlDescs))
			}
			ctx.wordNames.clear(); ctx.wordDescs.clear()
			ctx.trlNames.clear(); ctx.trlDescs.clear()
			ctx.inWords = true
		} else throw parseError("Expected ';' / '=' / '→' text, or '{'. Got '\\n'.")
		ctx.inNote = false
		lineStart = i + 1
		line++
	}

	private fun emitNote(ctx: ParserContext): WordListNote {
		if (ctx.wordNames.size != 1) throw parseError("A note line holds exactly one name.")
		val note = WordListNote(ctx.wordNames[0], ctx.wordDescs[0])
		ctx.wordNames.clear(); ctx.wordDescs.clear()
		return note
	}

	private fun flush(ctx: ParserContext) {
		val sb = ctx.sb
		// whitespace-only pieces trim to no name — they add nothing
		if (sb.isBlank()) return
		// note lines unescape already — only word/translation names carry
		// the doubled boundary run
		val name = if (ctx.inNote) sb.toString().trim() else halveTrailingBackslashes(sb.toString().trim())
		if (ctx.inWords) {
			ctx.wordNames.add(name)
			ctx.wordDescs.add(null)
		} else {
			ctx.trlNames.add(name)
			ctx.trlDescs.add(null)
		}
		sb.setLength(0)
	}

	/**
	 * Halves a name-final backslash run — the writer doubles the run (a lone
	 * trailing `\` would otherwise escape the next separator), so the reader
	 * halves it back — stable across every export cycle.
	 */
	private fun halveTrailingBackslashes(name: String): String {
		var run = 0
		while (name.length > run && name[name.length - 1 - run] == '\\') run++
		// an odd run cannot reach a boundary — its last `\` escapes the
		// separator, leaving nothing to halve
		if (run < 2 || run % 2 != 0) return name
		return name.dropLast(run / 2)
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
					if (i + 1 >= src.length) throw parseError("Expected ']' (end of description section). Reached end of file.")
					sb.append(src[++i])
				}
				']' -> return sb.toString().trim()
				// a newline inside the description is description content — the
				// writer's decorative [\n … \n] wrapper trims away
				// A `\r` rides before a CRLF pair and is dropped — a lone one is a newline.
				'\r' -> if (src.getOrNull(i + 1) != '\n') {
					line++
					lineStart = i + 1
					sb.append('\n')
				}
				'\n' -> {
					line++
					lineStart = i + 1
					sb.append(ch)
				}
				else -> sb.append(ch)
			}
		}
		throw parseError("Expected ']' (end of description section). Reached end of file.")
	}

	private fun parseError(info: String): IllegalArgumentException =
		IllegalArgumentException(
			"$containerName - line $line:\n'${src.substring(lineStart, minOf(i + 1, src.length))}'\n$info"
		)
}

/**
 * The word-list format as an import source — the parsed tree as a
 * [StreamItem] stream (see [DataImporter]).
 * - top-level items become root items in file order, no wrapper.
 */
class WordListImporter(containerName: String, text: String) : DataImporter {
	override val items: List<StreamItem>
	override val references: List<StreamReference> = emptyList()

	init {
		val root = WordListReader.parse(text, containerName)
		val out = ArrayList<StreamItem>()
		addEntries(out, root.entries, null)
		items = out
	}

	private fun addEntries(out: MutableList<StreamItem>, entries: List<WordListChild>, parent: StreamItem?) {
		for (entry in entries) when (entry) {
			is WordListWord -> addWord(out, entry, parent)
			is WordListNote ->
				StreamBuilder.addItem(out, ItemKind.NOTE, entry.name, entry.description ?: "", parent)
			is WordListChapter -> addChapter(out, entry, parent)
		}
	}

	private fun addChapter(out: MutableList<StreamItem>, chapter: WordListChapter, parent: StreamItem?) {
		val ref = StreamBuilder.addChapter(out, chapter.name, chapter.description ?: "", parent)
		addEntries(out, chapter.entries, ref)
	}

	private fun addWord(out: MutableList<StreamItem>, word: WordListWord, parent: StreamItem?) {
		val ref = StreamBuilder.addWord(out, word.name, word.description ?: "", parent)
		for (trl in word.translations)
			StreamBuilder.addTranslation(out, trl.name, trl.description ?: "", ref)
	}
}

class WordListExporter : DataExporter {
	override fun render(items: List<StreamItem>): String = WordListWriter.write(items)
}
