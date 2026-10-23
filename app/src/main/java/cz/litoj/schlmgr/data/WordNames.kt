package cz.litoj.schlmgr.data

/**
 * Stored-name parsing:
 * - slash alternatives and group parentheses expand through [parseVariants].
 * - backslash-separated editor fields split through [splitEscaped].
 */
object WordNames {

	/**
	 * - a slash separates alternative forms, so each form appears once.
	 * - a group in parentheses expands each alternative with the surrounding text.
	 * - an escaped slash, parenthesis or backslash stays literal.
	 */
	@JvmStatic
	fun parseVariants(name: String): List<String> =
		if (name.contains("/")) NameVariantParser(name).result else listOf(name)

	/**
	 * - a backslash starts a new word, unless it escapes the next character.
	 * - the escaped syntax characters are backslash, slash and both parentheses.
	 * - a trailing backslash stays.
	 */
	@JvmStatic
	fun splitEscaped(name: String): List<String> {
		val words = ArrayList<String>()
		var start = 0
		var i = 0
		while (i < name.length) {
			if (name[i] == '\\') {
				val next = name.getOrNull(i + 1)
				if (next != null && next != '\\' && next != '/' && next != '(' && next != ')') {
					words.add(name.substring(start, i))
					start = i + 1
				} else i++
			}
			i++
		}
		words.add(name.substring(start))
		return words
	}
}

/**
 * Variant parser for slash alternatives and parenthesized groups, with backslash escapes.
 * Segments break on a period, an exclamation mark, a question mark, a comma and a space.
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

	private fun getParts(ret: MutableList<StringBuilder>, group: Boolean = false) {
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
							else for (part in ret) part.append(sb)
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
						}
						sb = StringBuilder(0)
						recursiveParts = null
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
							for (part in parts) ret.add(if (part.length > 0) part.append(now) else part)
						} else {
							val size = ret.size
							for (j in 0 until size) {
								val retPart = ret.removeAt(0)
								for (part in parts)
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
				// a closing paren inside a group ends it, and a stray one is text
				')' -> if (group) break@renderer else sb.append(now)
				'(' -> {
					i++
					recursiveParts = ArrayList(2)
					if (sb.length > 0) recursiveParts.add(sb)
					sb = StringBuilder(8)
					getParts(recursiveParts, group = true)
					if (recursiveParts.size == 1) {
						if (sb.length > 0) sb.append(recursiveParts[0])
						else sb = recursiveParts[0]
						recursiveParts = null
					} else if (recursiveParts.isEmpty()) {
						// an empty group carries nothing, so the pending text goes on
						recursiveParts = null
					}
				}
				'\\' -> {
					i++
					// a trailing backslash escapes nothing, so it is dropped, not crashed on
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
					else for (part in ret) part.append(sb)
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
			} else if (parts.isEmpty()) parts.add(sb)
			// a pending-empty tail after real parts adds nothing, since a trailing
			// separator or empty group is not an empty variant
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
