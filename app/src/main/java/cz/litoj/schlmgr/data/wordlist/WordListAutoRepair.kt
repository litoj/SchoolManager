package cz.litoj.schlmgr.data.wordlist

/** One source line the import changed on its own, for the success report. */
class WordListAutoFix(val lineNumber: Int, val before: String, val after: String)

/** The tree of a word list and the repairs its buffer needed. */
class WordListRepaired(val root: WordListChapter, val fixes: List<WordListAutoFix>)

/**
 * Applies the confirmed suggestions of the parser while each is a single character.
 * - spaces and tabs are no character here, the writers trim them anyway.
 * - the whole buffer re-parses after every edit, so a later line never hides behind an earlier one.
 * - the picked file stays untouched, only the import buffer changes.
 */
internal object WordListAutoRepair {

	/** The repair count one file may reach, which bounds a line that keeps failing. */
	const val MAX_AUTO_FIXES = 64

	/** @throws WordListParseException on the first error no single character can mend. */
	fun parse(source: String, containerName: String): WordListRepaired {
		val fixes = ArrayList<WordListAutoFix>()
		var buffer = source
		while (fixes.size < MAX_AUTO_FIXES) {
			val error = try {
				return WordListRepaired(WordListReader.parse(buffer, containerName), fixes)
			} catch (e: WordListParseException) {
				e
			}
			val repair = singleCharRepair(error) ?: throw error
			fixes += WordListAutoFix(error.lineNumber, error.sourceLine, repair.text)
			buffer = replacingLine(buffer, error.lineNumber, repair.text)
		}
		return WordListRepaired(WordListReader.parse(buffer, containerName), fixes)
	}

	/** The most likely suggestion that edits one character of its own line. */
	private fun singleCharRepair(error: WordListParseException): WordListRepair? =
		error.repairs.firstOrNull {
			it.lineScoped && it.placeholder == null && '\n' !in it.text &&
				oneCharEdit(error.sourceLine, it.text)
		}

	/** whether the two differ by one character, spaces and tabs aside */
	private fun oneCharEdit(before: String, after: String): Boolean {
		val a = before.filterNot { it.isWhitespace() }
		val b = after.filterNot { it.isWhitespace() }
		return when {
			a.length == b.length -> a.indices.count { a[it] != b[it] } == 1
			a.length + 1 == b.length -> isInsertion(a, b)
			b.length + 1 == a.length -> isInsertion(b, a)
			else -> false
		}
	}

	/** whether [longer] is [shorter] with one character added */
	private fun isInsertion(shorter: String, longer: String): Boolean {
		var at = 0
		while (at < shorter.length && shorter[at] == longer[at]) at++
		return shorter.regionMatches(at, longer, at + 1, shorter.length - at)
	}

	private fun replacingLine(source: String, lineNumber: Int, line: String): String {
		val start = lineStartOffset(source, lineNumber)
		var end = start
		while (end < source.length && source[end] != '\n' && source[end] != '\r') end++
		return source.substring(0, start) + line + source.substring(end)
	}
}
