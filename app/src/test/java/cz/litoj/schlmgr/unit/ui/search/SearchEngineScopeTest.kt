package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A scoped run reports the rows of its scope only.
 * - the scope arrives closed, so the engine reads membership and never the tree.
 * - an empty scope reports the whole container.
 */
class SearchEngineScopeTest {

	private fun item(id: Int, name: String, kind: ItemKind) =
		DbItem(id = id, name = name, description = "", type = kind)

	private val root = item(1, "root", ItemKind.CHAPTER)
	private val cat = item(2, "cat", ItemKind.WORD)
	private val catTrl = item(3, "kocka", ItemKind.TRANSLATION)
	private val dog = item(4, "dog", ItemKind.WORD)
	private val dogTrl = item(5, "pes", ItemKind.TRANSLATION)

	private val index = SearchIndex(
		rows = listOf(root, cat, catTrl, dog, dogTrl),
		links = listOf(
			DbLink(parentId = root.id, childId = cat.id, position = 0),
			DbLink(parentId = root.id, childId = dog.id, position = 1),
			DbLink(parentId = cat.id, childId = catTrl.id),
			DbLink(parentId = dog.id, childId = dogTrl.id),
		),
	)

	/** The names the run reports, in walk order. */
	private fun hits(query: String, scope: Set<Int>): List<String> {
		val reported = mutableListOf<String>()
		SearchEngine(SearchQuery(text = query)).search(index, listOf(root), scope) { item, _, _ -> reported.add(item.name) }
		return reported
	}

	@Test
	fun `an empty scope reports the whole container`() {
		assertEquals(listOf("cat"), hits("ock", emptySet()))
		assertEquals(listOf("dog"), hits("pes", emptySet()))
	}

	@Test
	fun `a scope keeps the name match of its own rows`() {
		val scope = setOf(cat.id, catTrl.id)
		assertEquals(listOf("cat"), hits("cat", scope))
	}

	@Test
	fun `a scope hides a matching row outside it`() {
		assertEquals(emptyList<String>(), hits("dog", setOf(cat.id, catTrl.id)))
	}

	@Test
	fun `a row outside the scope stays hidden through its translation`() {
		assertEquals(listOf("cat"), hits("kocka", setOf(cat.id, catTrl.id)))
		assertEquals(emptyList<String>(), hits("pes", setOf(cat.id, catTrl.id)))
	}

	@Test
	fun `a container in the scope reports its matching children`() {
		assertEquals(listOf("dog"), hits("pes", setOf(dog.id, dogTrl.id)))
	}
}
