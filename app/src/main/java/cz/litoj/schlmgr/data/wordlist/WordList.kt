package cz.litoj.schlmgr.data.wordlist

import cz.litoj.schlmgr.data.DataExporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference
import cz.litoj.schlmgr.data.StreamBuilder

// the plain parse tree of a word-list file, with no database involved.
// - the parser stays testable, and the caller decides where the tree lands.
class WordListEntry(var name: String, var description: String? = null)

/** The parser state at a failure, kept separate from its localized presentation. */
enum class WordListProblem {
	INVALID_SEPARATOR,
	INCOMPLETE_ENTRY,
	SYMBOL_ONLY,
	MISSING_CHAPTER_OPEN,
	MISSING_TRANSLATION,
	TRANSLATION_BEFORE_DESCRIPTION,
	EXTRA_SEPARATOR,
	MISSING_NOTE_TEXT,
	NOTE_DIVIDER,
	NOTE_SECOND_SEPARATOR,
	EMPTY_VARIANT,
	MISSING_CHAPTER_NAME,
	CHAPTER_DIVIDER,
	DESCRIPTION_WITHOUT_TEXT,
	DESCRIPTION_END,
	DESCRIPTION_CLOSING,
	CHAPTER_END,
	CHAPTER_BRACES,
	INCOMPLETE_ESCAPE,
}

/** The kind of text only the user can supply, marked inside a repaired line. */
enum class WordListPlaceholder {
	CHAPTER,
	TEXT,
	TRANSLATION,
	NOTE,
	VARIANT,
}

/** The kind of one-edit fix, which decides the sentence the popup shows. */
enum class WordListRepairKind {
	SEPARATOR_SWAP,
	TRANSLATION_TEXT,
	TRANSLATION_ORDER,
	NOTE_TEXT,
	CHAPTER_NAME,
	CHAPTER_NAMED_BLOCK,
	CHAPTER_OPEN,
	CHAPTER_BLOCK,
	CHAPTER_CLOSE,
	DESCRIPTION_CLOSE,
	VARIANT_TEXT,
	VARIANT_DIVIDER,
	DESCRIBED_TEXT,
	ESCAPED_SEPARATOR,
	DOUBLED_BACKSLASH,
}

/**
 * A one-edit fix the parser confirmed against the whole file.
 * - [text] is the line as it should read, [WordListRepair.MISSING_TEXT] marks the open spot.
 * - a blank [text] means the line holds nothing the format accepts.
 */
class WordListRepair(
	val kind: WordListRepairKind,
	val text: String,
	val placeholder: WordListPlaceholder?,
) {
	companion object {
		/** stands for user text, so a suggestion never invents content */
		const val MISSING_TEXT = '\u0000'
	}
}

/** A located word-list failure, with the source details needed by the popup. */
class WordListParseException(
	val containerName: String,
	val lineNumber: Int,
	val sourceLine: String,
	val problemIndex: Int,
	val firstSeparator: Char?,
	val problem: WordListProblem,
	val repairs: List<WordListRepair> = emptyList(),
) : IllegalArgumentException("$containerName:$lineNumber: $sourceLine")

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
 * The mutable line assembly of [loadContent] for one chapter.
 * - the pending text plus the words and translations collected since the last newline.
 * - the side of the separator that the parser is on.
 * - whether the line is a note.
 */
private class ParserContext(var inWords: Boolean = true) {
	var inNote = false
	val sb = StringBuilder()
	val wordNames = ArrayList<String>()
	val variantDividers = ArrayList<Int>()
	val wordDescs = ArrayList<String?>()
	val trlNames = ArrayList<String>()
	val trlDescs = ArrayList<String?>()
}

private data class SourcePosition(val lineNumber: Int, val lineStart: Int, val characterIndex: Int)

/** One candidate fix applied to the failing source. */
private class Repair(
	val source: String,
	val text: String,
	val kind: WordListRepairKind,
	val placeholder: WordListPlaceholder? = null,
) {
	/** marks the spot a probe still found empty */
	fun withTextAt(failure: ProbeFailure): Repair {
		val mark = paddedMark(failure.sourceLine, failure.problemIndex)
		val at = (lineStartOffset(source, failure.lineNumber) + failure.problemIndex).coerceIn(0, source.length)
		return Repair(
			source.replaceRange(at, at, mark),
			failure.sourceLine.replaceRange(failure.problemIndex, failure.problemIndex, mark),
			kind,
			placeholderOf(failure.problem),
		)
	}
}

private val MISSING_MARK = WordListRepair.MISSING_TEXT.toString()

/** Marked text needs a space wherever the syntax offers none. */
private fun paddedMark(line: String, at: Int): String = when {
	at >= line.length -> " $MISSING_MARK"
	line[at] == ' ' || line[at] == '\t' -> MISSING_MARK
	else -> "$MISSING_MARK "
}

/** Where a repair probe stopped. */
private class ProbeFailure(
	val lineNumber: Int,
	val problem: WordListProblem,
	val sourceLine: String,
	val problemIndex: Int,
)

private fun placeholderOf(problem: WordListProblem): WordListPlaceholder? = when (problem) {
	WordListProblem.MISSING_TRANSLATION -> WordListPlaceholder.TRANSLATION
	WordListProblem.MISSING_NOTE_TEXT -> WordListPlaceholder.NOTE
	WordListProblem.MISSING_CHAPTER_NAME -> WordListPlaceholder.CHAPTER
	WordListProblem.EMPTY_VARIANT -> WordListPlaceholder.VARIANT
	else -> null
}

/** Counts line starts as the reader does: CR, LF or a CRLF pair ends one. */
private fun lineStartOffset(source: String, lineNumber: Int): Int {
	var start = 0
	repeat(lineNumber - 1) {
		while (start < source.length && source[start] != '\n' && source[start] != '\r') start++
		if (start < source.length) start += if (source[start] == '\r' && source.getOrNull(start + 1) == '\n') 2 else 1
	}
	return start
}

/**
 * The word-list text format:
 * - a chapter is a name, an opening brace, its content, then a closing brace.
 * - a word line splits the name from its translation on a semicolon, an equals sign or an arrow.
 * - a description follows the text in square brackets.
 * - a separator-led line is a note, a word line without the word.
 */
class WordListReader private constructor(
	private val src: String,
	private val containerName: String,
	private val probing: Boolean = false,
) {

	private var i = -1
	private var lineStart = 0
	private var line = 1
	private var firstSeparator: Char? = null

	companion object {

		private const val MAX_CANDIDATES = 5
		private const val MAX_REPAIRS = 3

		/**
		 * - the containerName labels the parse errors, since the import names the target.
		 *
		 * @throws IllegalArgumentException on malformed input, with a positioned message
		 */
		@JvmStatic
		fun parse(source: String, containerName: String): WordListChapter {
			val reader = WordListReader(source, containerName)
			val root = WordListChapter(containerName)
			reader.loadContent(root.entries)
			return root
		}
	}

	private fun loadContent(
		entries: MutableList<WordListChild>,
		chapterOpening: SourcePosition? = null,
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
					val next = src.getOrNull(i + 1)
					if (next == null || next == '\n' || next == '\r')
						throw parseError(WordListProblem.INCOMPLETE_ESCAPE)
					i++
					when (next) {
						// a note line unescapes its backslash, since notes carry no
						// variant syntax to keep raw
						'\\', '/', '(', ')' -> if (!ctx.inNote) sb.append('\\')
						';', '=', '→', '[', ']' -> {}
						else -> {
							if (ctx.inNote) throw parseError(
								WordListProblem.NOTE_DIVIDER, characterIndex = i - 1
							)
							// an unescaped backslash splits the current word and translation
							ctx.variantDividers += i - 1
							flush(ctx)
						}
					}
					sb.append(next)
				}
				';', '=', '→' -> {
					if (firstSeparator == null) firstSeparator = ch
					// a leading separator opens a note line, since no word precedes it
					if (!ctx.inNote && ctx.inWords && wordNames.isEmpty() && sb.isEmpty()) {
						ctx.inNote = true
					} else {
						if (ctx.inNote) throw parseError(WordListProblem.NOTE_SECOND_SEPARATOR)
						flush(ctx)
						if (ctx.inWords && wordNames.isNotEmpty()) {
							ctx.inWords = false
						} else throw parseError(WordListProblem.EXTRA_SEPARATOR)
					}
				}
				'\r' -> {
					// a lone CR is a line ending too, and only the CRLF pair consumes the
					// following newline
					if (src.getOrNull(i + 1) == '\n') i++
					onNewline(ctx, entries)
				}
				'\n' -> onNewline(ctx, entries)
				'}' -> {
					if (src.substring(lineStart, i).trim().isEmpty()) {
						// the root load has no chapter to close, so a stray brace
						// must not swallow the rest of the file
						if (chapterOpening == null) throw parseError(WordListProblem.CHAPTER_BRACES)
						while (++i < src.length && src[i] != '\n' && src[i] != '\r');
						if (i < src.length && src[i] == '\r' && src.getOrNull(i + 1) == '\n') i++
						lineStart = i + 1
						line++
						return
					}
					sb.append(ch)
				}
				'{' -> {
					val next = src.getOrNull(i + 1)
					val breakAfterBrace = next == null || next == '\n' || next == '\r'
					if (breakAfterBrace && ctx.inWords && !ctx.inNote) {
						val name: String
						val desc: String?
						val markedEmpty = wordNames.isEmpty() && sb.isNotEmpty() && sb.last() == '§'
						if (wordNames.isEmpty()) {
							// a section sign before a chapter opener marks an empty name,
							// so it is stripped, not shown
							if (markedEmpty) sb.setLength(sb.length - 1)
							name = halveTrailingBackslashes(sb.toString().trim())
							desc = null
						} else {
							// a backslash-divided name cannot name a chapter.
							// - its divider words would silently vanish.
							// - a lone divider leaves its tail text in sb.
							if (wordNames.size > 1 || sb.isNotEmpty()) throw parseError(
								WordListProblem.CHAPTER_DIVIDER,
								characterIndex = ctx.variantDividers.firstOrNull() ?: i,
							)
							name = wordNames[0]
							desc = wordDescs[0]
						}
						if (name.isEmpty() && !markedEmpty) throw parseError(WordListProblem.MISSING_CHAPTER_NAME)
						val opening = SourcePosition(line, lineStart, i)
						when (next) {
							'\r' -> {
								i++
								if (src.getOrNull(i + 1) == '\n') i++
							}
							'\n' -> i++
						}
						lineStart = i + 1
						if (next != null) line++
						wordNames.clear(); wordDescs.clear(); ctx.variantDividers.clear()
						sb.setLength(0)
						val child = WordListChapter(name, desc)
						entries.add(child)
						loadContent(child.entries, opening)
					} else sb.append(ch)
				}
				']' -> throw parseError(WordListProblem.DESCRIPTION_CLOSING)
				'[' -> {
					// a whitespace-only piece is no text either, since a blank name
					// would poison the refusals of the writers
					if (sb.isNotBlank()) {
						flush(ctx)
						val description = getDescription()
						if (ctx.inWords) wordDescs[wordDescs.size - 1] = description
						else trlDescs[trlDescs.size - 1] = description
					} else when {
						ctx.inNote -> throw parseError(WordListProblem.MISSING_NOTE_TEXT)
						!ctx.inWords -> throw parseError(WordListProblem.TRANSLATION_BEFORE_DESCRIPTION)
						ctx.variantDividers.isNotEmpty() -> throw parseError(
							WordListProblem.EMPTY_VARIANT,
							characterIndex = ctx.variantDividers.last(),
						)
						else -> throw parseError(WordListProblem.DESCRIPTION_WITHOUT_TEXT)
					}
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
		} else if (wordNames.isNotEmpty()) {
			if (ctx.inWords) failIncompleteLine(ctx)
			else throw parseError(WordListProblem.MISSING_TRANSLATION)
		}
		if (chapterOpening != null) throw parseError(
			WordListProblem.CHAPTER_END,
			chapterOpening.lineNumber,
			chapterOpening.lineStart,
			chapterOpening.characterIndex,
		)
	}

	private fun onNewline(ctx: ParserContext, entries: MutableList<WordListChild>) {
		flush(ctx)
		if (ctx.inNote) entries.add(emitNote(ctx))
		else if (ctx.inWords && ctx.wordNames.isEmpty()) {
			// blank line: nothing emits, and the error positions still advance
		} else if (!ctx.inWords && ctx.trlNames.isNotEmpty()) {
			ctx.wordNames.forEachIndexed { idx, w ->
				entries.add(mkWord(w, ctx.wordDescs[idx], ctx.trlNames, ctx.trlDescs))
			}
			ctx.wordNames.clear(); ctx.wordDescs.clear(); ctx.variantDividers.clear()
			ctx.trlNames.clear(); ctx.trlDescs.clear()
			ctx.inWords = true
		} else if (ctx.inWords) failIncompleteLine(ctx)
		else throw parseError(WordListProblem.MISSING_TRANSLATION)
		ctx.inNote = false
		lineStart = i + 1
		line++
	}

	private fun emitNote(ctx: ParserContext): WordListNote {
		if (ctx.wordNames.size != 1) throw parseError(WordListProblem.MISSING_NOTE_TEXT)
		val note = WordListNote(ctx.wordNames[0], ctx.wordDescs[0])
		ctx.wordNames.clear(); ctx.wordDescs.clear()
		return note
	}

	private fun flush(ctx: ParserContext) {
		val sb = ctx.sb
		if (sb.isBlank()) return
		// note lines unescape already, so only word and translation names carry
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
	 * - the writer doubles the run, so a lone trailing backslash cannot escape a separator.
	 * - the reader halves it back, so the cycle stays stable across every export.
	 */
	private fun halveTrailingBackslashes(name: String): String {
		var run = 0
		while (name.length > run && name[name.length - 1 - run] == '\\') run++
		// an odd run cannot reach a boundary, since its last backslash escapes
		// the separator, leaving nothing to halve
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
		val openingLine = line
		val openingLineStart = lineStart
		val openingIndex = i
		val sb = StringBuilder()
		while (++i < src.length) {
			when (val ch = src[i]) {
				'\\' -> {
					val next = src.getOrNull(i + 1)
					if (next == null || next == '\n' || next == '\r')
						throw parseError(WordListProblem.INCOMPLETE_ESCAPE)
					sb.append(src[++i])
				}
				']' -> return sb.toString().trim()
				// a newline inside the description is description content.
				// - the decorative newline wrapper of the writer trims away.
				// - a CR before a CRLF pair is dropped, and a lone one is a newline.
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
		throw parseError(WordListProblem.DESCRIPTION_END, openingLine, openingLineStart, openingIndex)
	}

	private fun failIncompleteLine(ctx: ParserContext): Nothing {
		val sourceLine = sourceLineAt(lineStart)
		val arrow = sourceLine.indexOf("->")
		val hasArrowRelation = arrow >= 0 &&
			sourceLine.substring(0, arrow).isNotBlank() &&
			sourceLine.substring(arrow + 2).isNotBlank()
		val nextLine = nextNonblankLine()
		val problem = when {
			ctx.variantDividers.isNotEmpty() && ctx.variantDividers.size >= ctx.wordNames.size ->
				WordListProblem.EMPTY_VARIANT
			hasArrowRelation -> WordListProblem.INVALID_SEPARATOR
			sourceLine.none { it.isLetterOrDigit() } -> WordListProblem.SYMBOL_ONLY
			nextLine != null && indentation(nextLine) > indentation(sourceLine) ->
				WordListProblem.MISSING_CHAPTER_OPEN
			else -> WordListProblem.INCOMPLETE_ENTRY
		}
		val characterIndex = if (problem == WordListProblem.EMPTY_VARIANT) ctx.variantDividers.last() else i
		throw parseError(problem, characterIndex = characterIndex)
	}

	private fun nextNonblankLine(): String? {
		var start = i
		while (start < src.length) {
			while (start < src.length && (src[start] == '\n' || src[start] == '\r')) start++
			var end = start
			while (end < src.length && src[end] != '\n' && src[end] != '\r') end++
			if (end > start) {
				val line = src.substring(start, end)
				if (line.isNotBlank()) return line
			}
			start = end
		}
		return null
	}

	private fun indentation(line: String): Int {
		var width = 0
		for (ch in line) when (ch) {
			' ' -> width++
			'\t' -> width += 4
			else -> return width
		}
		return width
	}

	private fun sourceLineAt(lineStart: Int): String {
		var lineEnd = lineStart
		while (lineEnd < src.length && src[lineEnd] != '\n' && src[lineEnd] != '\r') lineEnd++
		return src.substring(lineStart, lineEnd)
	}

	/**
	 * Single-edit fixes the parser confirms by re-parsing the whole file.
	 * - the failing line is blanked first, so a fix never hides another error.
	 * - a fix survives only while the spot it touched stays parseable.
	 */
	private fun repairsFor(
		problem: WordListProblem,
		lineNumber: Int,
		lineStart: Int,
		local: Int,
	): List<WordListRepair> {
		val baseline = probe(sourceReplacingLine(lineStart, ""))
		val out = ArrayList<WordListRepair>()
		for (candidate in candidatesFor(problem, lineStart, sourceLineAt(lineStart), local)) {
			if (out.size == MAX_REPAIRS) break
			val repair = repairOf(candidate, lineNumber, baseline) ?: continue
			if (out.none { it.text == repair.text && it.placeholder == repair.placeholder }) out += repair
		}
		return out
	}

	private fun repairOf(candidate: Repair, lineNumber: Int, baseline: ProbeFailure?): WordListRepair? {
		var fixed = candidate
		var failure = probe(fixed.source)
		if (failure != null && failure.lineNumber == lineNumber && placeholderOf(failure.problem) != null) {
			fixed = fixed.withTextAt(failure)
			failure = probe(fixed.source)
		}
		if (failure != null && failure.countsAgainst(lineNumber, baseline)) return null
		return WordListRepair(fixed.kind, fixed.text, fixed.placeholder)
	}

	/**
	 * Whether a probe error still blames the repaired spot.
	 * - an error the blanked line caused on its own stays outside this fix.
	 * - a line still lacking its own word or translation is a separate error.
	 */
	private fun ProbeFailure.countsAgainst(lineNumber: Int, baseline: ProbeFailure?): Boolean {
		if (problem == baseline?.problem && this.lineNumber == baseline.lineNumber) return false
		return this.lineNumber != lineNumber || problem != WordListProblem.INCOMPLETE_ENTRY
	}

	/** null means the text parses. */
	private fun probe(source: String): ProbeFailure? = try {
		WordListReader(source, containerName, probing = true).loadContent(arrayListOf())
		null
	} catch (e: WordListParseException) {
		ProbeFailure(e.lineNumber, e.problem, e.sourceLine, e.problemIndex)
	} catch (e: IllegalArgumentException) {
		ProbeFailure(0, WordListProblem.INCOMPLETE_ENTRY, "", 0)
	}

	/**
	 * The one-edit shapes worth probing, most likely first.
	 * - the parser decides which of them fix the file, none of them is a claim.
	 */
	private fun candidatesFor(
		problem: WordListProblem,
		lineStart: Int,
		line: String,
		at: Int,
	): List<Repair> {
		val separator = (firstSeparator ?: '=').toString()
		val out = ArrayList<Repair>()

		fun addAt(source: String, text: String, kind: WordListRepairKind, placeholder: WordListPlaceholder? = null) {
			if (out.size < MAX_CANDIDATES) out += Repair(source, text, kind, placeholder)
		}

		fun add(edited: String, kind: WordListRepairKind, placeholder: WordListPlaceholder? = null) =
			addAt(sourceReplacingLine(lineStart, edited), edited, kind, placeholder)

		fun replace(at: Int, span: Int, value: String, kind: WordListRepairKind) {
			if (at > line.length) return
			add(line.replaceRange(at, (at + span).coerceAtMost(line.length), value), kind)
		}

		/** the line with marked user text inserted at [at] */
		fun marked(at: Int) = line.replaceRange(at, at, paddedMark(line, at))

		/** text only the user can type, so the marked spot stays open */
		fun needsText(at: Int, kind: WordListRepairKind, placeholder: WordListPlaceholder) =
			add(marked(at), kind, placeholder)

		/** this line opens a chapter, the file gains its closing } */
		fun openChapter(edited: String, kind: WordListRepairKind, placeholder: WordListPlaceholder? = null) =
			addAt("${sourceReplacingLine(lineStart, edited)}\n}\n", "$edited\n…\n}", kind, placeholder)

		when (problem) {			WordListProblem.INVALID_SEPARATOR -> replace(at, "->".length, " $separator ", WordListRepairKind.SEPARATOR_SWAP)
			WordListProblem.MISSING_TRANSLATION ->
				needsText(at, WordListRepairKind.TRANSLATION_TEXT, WordListPlaceholder.TRANSLATION)
			WordListProblem.MISSING_NOTE_TEXT -> needsText(at, WordListRepairKind.NOTE_TEXT, WordListPlaceholder.NOTE)
			WordListProblem.TRANSLATION_BEFORE_DESCRIPTION ->
				needsText(at, WordListRepairKind.TRANSLATION_ORDER, WordListPlaceholder.TRANSLATION)
			WordListProblem.MISSING_CHAPTER_NAME -> {
				needsText(at, WordListRepairKind.CHAPTER_NAME, WordListPlaceholder.CHAPTER)
				openChapter(marked(at), WordListRepairKind.CHAPTER_NAMED_BLOCK, WordListPlaceholder.CHAPTER)
			}
			WordListProblem.EMPTY_VARIANT ->
				needsText((at + 1).coerceAtMost(line.length), WordListRepairKind.VARIANT_TEXT, WordListPlaceholder.VARIANT)
			WordListProblem.DESCRIPTION_WITHOUT_TEXT -> {
				needsText(at, WordListRepairKind.DESCRIBED_TEXT, WordListPlaceholder.TEXT)
				add("$separator $line", WordListRepairKind.NOTE_TEXT)
			}
			WordListProblem.INCOMPLETE_ENTRY -> {
				replace(at, 0, " $separator", WordListRepairKind.TRANSLATION_TEXT)
				replace(at, 0, " {", WordListRepairKind.CHAPTER_OPEN)
			}
			WordListProblem.MISSING_CHAPTER_OPEN -> {
				replace(at, 0, " {", WordListRepairKind.CHAPTER_OPEN)
				openChapter("${line.trimEnd()} {", WordListRepairKind.CHAPTER_BLOCK)
			}
			WordListProblem.SYMBOL_ONLY -> replace(at, 1, "}", WordListRepairKind.CHAPTER_CLOSE)
			WordListProblem.EXTRA_SEPARATOR -> {
				replace(at, 1, "\\", WordListRepairKind.VARIANT_DIVIDER)
				replace(at, 0, "\\", WordListRepairKind.ESCAPED_SEPARATOR)
			}
			WordListProblem.NOTE_SECOND_SEPARATOR ->
				// a note keeps its separator as text, so the backslash goes in front of it
				replace(at, 0, "\\", WordListRepairKind.ESCAPED_SEPARATOR)
			WordListProblem.NOTE_DIVIDER, WordListProblem.INCOMPLETE_ESCAPE ->
				replace(at, 0, "\\", WordListRepairKind.DOUBLED_BACKSLASH)
			WordListProblem.DESCRIPTION_END -> add("$line]", WordListRepairKind.DESCRIPTION_CLOSE)
			WordListProblem.DESCRIPTION_CLOSING, WordListProblem.CHAPTER_BRACES, WordListProblem.CHAPTER_DIVIDER -> Unit
			WordListProblem.CHAPTER_END -> addAt("$src\n}\n", "}", WordListRepairKind.CHAPTER_CLOSE)
		}
		return out
	}

	private fun sourceReplacingLine(lineStart: Int, line: String): String {
		var lineEnd = lineStart
		while (lineEnd < src.length && src[lineEnd] != '\n' && src[lineEnd] != '\r') lineEnd++
		return src.substring(0, lineStart) + line + src.substring(lineEnd)
	}

	private fun parseError(
		problem: WordListProblem,
		lineNumber: Int = line,
		lineStart: Int = this.lineStart,
		characterIndex: Int = i,
	): WordListParseException {
		val sourceLine = sourceLineAt(lineStart)
		val endIndex = (characterIndex - lineStart).coerceIn(0, sourceLine.length)
		val problemIndex = when (problem) {
			WordListProblem.INVALID_SEPARATOR -> sourceLine.indexOf("->").coerceAtLeast(0)
			WordListProblem.SYMBOL_ONLY -> sourceLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
			else -> endIndex
		}
		return WordListParseException(
			containerName,
			lineNumber,
			sourceLine,
			problemIndex,
			firstSeparator,
			problem,
			if (probing) emptyList() else repairsFor(problem, lineNumber, lineStart, problemIndex),
		)
	}
}

/**
 * - top-level items become root items in file order, with no wrapper.
 * - see [DataImporter] for the stream contract.
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
