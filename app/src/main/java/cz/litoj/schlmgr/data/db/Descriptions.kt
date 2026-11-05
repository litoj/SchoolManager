package cz.litoj.schlmgr.data.db

/**
 * Merges descriptions: an incoming value joins after a newline unless it is blank or contained.
 * Pure, which keeps the repository facade persistence-only.
 */
internal object Descriptions {

	fun merge(stored: String, incoming: String?): String {
		val trimmedStored = stored.trim()
		val trimmedIncoming = incoming?.trim()
		if (trimmedIncoming.isNullOrEmpty()) return trimmedStored
		// containment is whole-text or line-based, so a distinct description
		// that happens to be a substring of a stored line still joins
		if (trimmedStored == trimmedIncoming) return trimmedStored
		val storedLines = trimmedStored.lines().toHashSet()
		if (trimmedIncoming.lines().all { it in storedLines }) return trimmedStored
		return if (trimmedStored.isEmpty()) trimmedIncoming else trimmedStored + '\n' + trimmedIncoming
	}
}
