package cz.litoj.schlmgr.data

/**
 * Stored-name parsing:
 * - `/` alternatives and `()` groups expand ([parseVariants]).
 * - `\`-separated editor fields split ([splitEscaped]).
 */
object WordNames {

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
	 * Splits an editor field into words/translations. A `\` starts a new
	 * word, unless it escapes the next character (`\\`, `\/`, `\(`, `\)`) —
	 * a trailing `\` stays.
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
				} else i++ // an escape pair stays in the word, a trailing `\` too
			}
			i++
		}
		words.add(name.substring(start))
		return words
	}
}

/**
 * Variant parser for `/` alternatives and `(...)` groups — `\` escapes.
 * Segments break on `. ! ? ,` and space.
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
				// a closing paren inside a group ends it — a stray one is text
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
						// an empty group carries nothing — the pending text goes on
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
			// a pending-empty tail after real parts adds nothing — a
			// trailing separator or empty group is not an empty variant
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
