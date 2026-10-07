package cz.litoj.schlmgr.db.dao

import androidx.room.Dao
import androidx.room.Query
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind

/**
 * Word-specific queries plus the word-string parsing used for test answer
 * matching and display.
 *
 * The parsing is the port of the former engine `NameReader`/`SimpleReader`
 * codecs, kept 1:1 so the stored word strings behave exactly as before:
 *  - [parseVariants] — the `/` alternatives and `()` groups of a name expand
 *    into every plain form the name can be shown or answered as;
 *  - [splitEscaped] — a `\`-separated editor field splits into several words.
 */
@Dao
abstract class WordDao {

	/** The translation rows of a word, in link order. */
	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id = :wordId AND i.type = :kind ORDER BY l.position, l.child_id"
	)
	abstract suspend fun getTranslations(wordId: Int, kind: ItemKind = ItemKind.TRANSLATION): List<DbItem>

	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT i.* FROM item i WHERE i.id IN (SELECT id FROM des) " +
			"AND i.name = :name AND i.type = :kind LIMIT 1"
	)
	abstract suspend fun findWordInSubject(rootId: Int, name: String, kind: ItemKind = ItemKind.WORD): DbItem?

	companion object {

		/**
		 * Reads a word string and gives back all its name possibilities.
		 *
		 * Examples (the actual parser results):
		 *  - `"They/(He and she) moved."` → `[They moved., He and she moved.]`
		 *  - `"/I/You smile."` → `[smile., I smile., You smile.]`
		 *  - `"(I/You smile.)/(Smile!)"` → `[I smile., You smile., Smile!]`
		 *  - `"I 'm/am here/there."` → `[I 'm here., I 'm there., I am here., I am there.]`
		 */
		@JvmStatic
		fun parseVariants(name: String): List<String> =
			if (name.contains("/")) NameVariantParser(name).result else listOf(name)

		/**
		 * Splits an editor field into separate words/translations: the `\`
		 * divider splits, unless escaped (`\\`) or part of the `/()` variant
		 * syntax.
		 */
		@JvmStatic
		fun splitEscaped(name: String): List<String> {
			val words = ArrayList<String>()
			var start = 0
			var prev = '\uFFFF'
			for (i in name.indices) {
				val ch = name[i]
				if (prev == '\\' && ch != '\\' && ch != '/' && ch != '(' && ch != ')') {
					words.add(name.substring(start, i - 1))
					start = i
				}
				prev = ch
			}
			words.add(name.substring(start))
			return words
		}
	}
}

/**
 * The variant parser itself (the former engine `NameReader`, ported 1:1).
 *
 * Grammar inside one name:
 *  - `/` marks alternatives of a segment;
 *  - `( ... )` groups a segment so alternatives apply to it as a whole;
 *  - `\` escapes the next character;
 *  - the segment boundaries are `.`, `!`, `?`, `,` and space.
 */
private class NameVariantParser(private val str: String) {

	private val length = str.length
	private var i = 0

	val result: List<String>

	init {
		val parts = ArrayList<StringBuilder>(3)
		getParts(parts)
		result = parts.map { it.toString() }
	}

	private fun getParts(ret: MutableList<StringBuilder>) {
		var parts: MutableList<StringBuilder>? = null
		var recursiveParts: MutableList<StringBuilder>? = null
		var sb = StringBuilder(8)
		var lastSplitter = i - 1
		renderer@ while (i < length) {
			val now = str[i]
			when (now) {
				'/' -> {
					if (recursiveParts == null) {
						if (sb.length > 0 || lastSplitter == i - 1) {
							if (parts == null) parts = ArrayList(3)
							parts.add(sb)
							sb = StringBuilder(8)
						}
					} else {
						if (parts == null) {
							if (sb.length > 0) for (part in recursiveParts) part.append(sb)
							parts = recursiveParts
						} else for (part in recursiveParts) parts.add(part.append(sb))
						sb = StringBuilder(0)
						recursiveParts = null
					}
					lastSplitter = i
				}
				'.', '!', '?', ',', ' ' -> {
					if (parts == null) {
						if (recursiveParts == null) {
							sb.append(now)
							if (ret.isEmpty()) ret.add(sb)
							else for (part in ret) if (sb.length > 0) part.append(sb)
							sb = StringBuilder(8)
						} else {
							if (ret.isEmpty()) {
								for (part in recursiveParts) ret.add(part.append(sb).append(now))
							} else {
								val size = ret.size
								for (j in 0 until size) {
									val retPart = ret.removeAt(0)
									for (part in recursiveParts)
										ret.add(
											if (part.length > 0) StringBuilder(retPart).append(part).append(sb).append(now)
											else StringBuilder(retPart).append(sb)
										)
								}
							}
							sb = StringBuilder(0)
							recursiveParts = null
						}
					} else {
						if (sb.length > 0) {
							if (recursiveParts == null) parts.add(sb)
							else {
								for (part in recursiveParts) parts.add(part.append(sb))
								recursiveParts = null
							}
							sb = StringBuilder(8)
						} else if (recursiveParts != null) {
							for (part in recursiveParts) parts.add(part)
							recursiveParts = null
						}
						if (ret.isEmpty()) {
							for (part in parts!!) ret.add(if (part.length > 0) part.append(now) else part)
						} else {
							val size = ret.size
							for (j in 0 until size) {
								val retPart = ret.removeAt(0)
								for (part in parts!!)
									ret.add(
										if (part.length > 0) StringBuilder(retPart).append(part).append(now)
										else StringBuilder(retPart).append(part)
									)
							}
						}
						parts = null
					}
					lastSplitter = i
				}
				')' -> break@renderer
				'(' -> {
					i++
					recursiveParts = ArrayList(2)
					if (sb.length > 0) recursiveParts.add(sb)
					sb = StringBuilder(8)
					getParts(recursiveParts)
					if (recursiveParts.size == 1) {
						if (sb.length > 0) sb.append(recursiveParts[0])
						else sb = recursiveParts[0]
						recursiveParts = null
					}
				}
				'\\' -> {
					i++
					// a trailing `\` escapes nothing — dropped, not crashed on
					if (i < length) sb.append(str[i])
				}
				else -> sb.append(now)
			}
			i++
		}
		if (parts == null) {
			if (recursiveParts == null) {
				if (sb.length > 0) {
					if (ret.isEmpty()) ret.add(sb)
					else for (part in ret) if (sb.length > 0) part.append(sb)
				}
			} else {
				if (ret.isEmpty()) for (part in recursiveParts) ret.add(part.append(sb))
				else {
					val size = ret.size
					for (j in 0 until size) {
						val retPart = ret.removeAt(0)
						for (part in recursiveParts)
							ret.add(StringBuilder(retPart).append(part).append(sb))
					}
				}
			}
		} else {
			if (sb.length > 0) {
				if (recursiveParts == null) parts.add(sb)
				else for (part in recursiveParts) parts.add(part.append(sb))
			} else if (recursiveParts != null) {
				for (part in recursiveParts) parts.add(part)
			} else parts.add(sb)
			if (ret.isEmpty()) for (part in parts) ret.add(part)
			else {
				val size = ret.size
				for (j in 0 until size) {
					val retPart = ret.removeAt(0)
					for (part in parts) ret.add(StringBuilder(retPart).append(part))
				}
			}
		}
	}
}
