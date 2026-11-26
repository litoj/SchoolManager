package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind

/** The graph data one run needs after its preparation finishes. */
internal class TestRunData private constructor(
	val entries: List<TestEntry>,
	private val parents: Map<Int, List<DbItem>>,
) {

	fun parentsOf(id: Int): List<DbItem> = parents[id].orEmpty()

	companion object {

		/** The setup caches entries at the limit 100, so a later limit change needs no reread. */
		suspend fun readEntries(
			sources: List<List<DbItem>>, graphs: GraphQueries, limit: Int = TestEngine.maxRate,
		): List<TestEntry> {
			val children = mutableMapOf<Int, List<DbItem>>()
			suspend fun loadChildren(id: Int) {
				if (children.containsKey(id)) return
				val rows = graphs.children(id)
				children[id] = rows
				for (row in rows) if (row.type == ItemKind.CHAPTER) loadChildren(row.id)
			}
			for (path in sources) {
				val selected = path.last()
				if (selected.type == ItemKind.CHAPTER) loadChildren(selected.id)
			}
			val entries = TestEngine.convertAll(sources, limit) { id -> children[id].orEmpty() }
			val translations = mutableMapOf<Int, List<DbItem>>()
			val siblingGroups = entries.groupBy { entry -> entry.path.dropLast(1).lastOrNull()?.id }
			for ((parentId, siblings) in siblingGroups) {
				if (parentId == null) {
					for (entry in siblings) translations[entry.word.id] = graphs.translations(entry.word.id)
					continue
				}
				val batch = graphs.childTranslations(parentId)
				for (entry in siblings) translations[entry.word.id] = batch[entry.word.id].orEmpty()
			}
			return entries.map { entry ->
				TestEntry(entry.word, entry.path, translations[entry.word.id].orEmpty())
			}
		}

		suspend fun read(sources: List<List<DbItem>>, graphs: GraphQueries): TestRunData {
			val prepared = readEntries(sources, graphs)
			val parents = prepared.flatMap { it.translations() }.distinctBy(DbItem::id).associate { translation ->
				translation.id to graphs.parentRows(translation.id)
			}
			return TestRunData(prepared, parents)
		}
	}
}
