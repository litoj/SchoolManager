package cz.litoj.schlmgr.data.db

/**
 * Merges descriptions — incoming joins after newline unless blank or contained.
 * Pure, keeps the repository facade persistence-only.
 */
internal object Descriptions {

	fun merge(stored: String, incoming: String?): String {
		if (incoming.isNullOrEmpty()) return stored
		// containment is whole-text or line-based — a distinct description
		// that happens to be a substring of a stored line still joins
		if (stored == incoming) return stored
		// a multi-line value counts as contained when all its lines landed
		val storedLines = stored.lines().toHashSet()
		if (incoming.lines().all { it in storedLines }) return stored
		return if (stored.isEmpty()) incoming else stored + '\n' + incoming
	}
}
