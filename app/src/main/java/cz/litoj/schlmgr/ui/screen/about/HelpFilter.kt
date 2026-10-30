package cz.litoj.schlmgr.ui.screen.about

/**
 * Picks the help sections visible for a search query.
 * - a section stays when its own text or any descendant matches.
 * - the caller resolves the resource ids, so the filter stays pure and testable.
 */
object HelpFilter {

	/** The [HelpSection.title] ids that stay visible for the query. */
	fun visible(root: List<HelpSection>, query: String, resolve: (Int) -> String): Set<Int> {
		val needle = query.trim().lowercase()
		val visible = mutableSetOf<Int>()
		fun walk(section: HelpSection): Boolean {
			// no short circuit: a self match must not hide a matching descendant
			val childHit = section.children.map { walk(it) }.any { it }
			val hit = matches(section, needle, resolve) || childHit
			if (hit) visible += section.title
			return hit
		}
		root.forEach { walk(it) }
		return visible
	}

	private fun matches(section: HelpSection, needle: String, resolve: (Int) -> String): Boolean =
		listOf(section.title, section.intro, section.body)
			.filter { it != 0 }
			.any { resolve(it).lowercase().contains(needle) }
}
