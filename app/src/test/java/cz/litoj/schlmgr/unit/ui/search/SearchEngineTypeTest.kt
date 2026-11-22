package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchIndex
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import org.junit.Assert.assertEquals
import org.junit.Test

/** The kind a translation answers for its word, while every other kind stays on the row. */
class SearchEngineTypeTest {

	private val root = DbItem(id = 1, name = "root", description = "", type = ItemKind.CHAPTER)
	private val word = DbItem(id = 2, name = "word", description = "", type = ItemKind.WORD)
	private val translation = DbItem(id = 3, name = "translation", description = "", type = ItemKind.TRANSLATION)
	private val bare = DbItem(id = 4, name = "bare", description = "", type = ItemKind.WORD)
	private val index = SearchIndex(
		listOf(root, word, translation, bare),
		listOf(
			DbLink(parentId = root.id, childId = word.id, position = 0),
			DbLink(parentId = root.id, childId = bare.id, position = 1),
			DbLink(parentId = word.id, childId = translation.id, position = 0),
		),
	)

	private fun hits(kind: ItemKind): List<String> {
		val query = SearchQuery(criteria = listOf(Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(kind))))
		val hits = mutableListOf<String>()
		SearchEngine(query).search(index, listOf(root)) { item, _, _ -> hits.add(item.name) }
		return hits
	}

	@Test
	fun `a translation type reports the word whose translation matched`() {
		assertEquals(listOf("word"), hits(ItemKind.TRANSLATION))
	}

	@Test
	fun `a word type stays on the word rows`() {
		assertEquals(listOf("word", "bare"), hits(ItemKind.WORD))
	}
}
