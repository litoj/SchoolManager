package cz.litoj.schlmgr.unit.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the engine answers to a query its editor built.
 * - the typed text searches names, a criterion searches the tag it names.
 * - every part of one query must hold.
 */
class SearchEngineMatchTest {

	private fun text(value: String) = SearchQuery(text = value)

	private fun Criterion.query() = SearchQuery(criteria = listOf(this))

	private fun nameCriterion(operator: FilterOperator, value: String) =
		Criterion(SearchTag.NAME, operator, CriterionValue.Text(value))

	private fun item(name: String, kind: ItemKind = ItemKind.WORD, description: String = "") =
		DbItem(name = name, description = description, type = kind)

	/** The names the query reports, one chapter row per name. */
	private fun hits(query: SearchQuery, vararg names: String) =
		answers(query, *names.map { item(it, ItemKind.CHAPTER) }.toTypedArray())

	private fun answers(query: SearchQuery, vararg items: DbItem): List<String> {
		val engine = SearchEngine(query)
		val matched = ArrayList<String>()
		for ((i, row) in items.withIndex()) {
			// distinct ids, because the engine dedups emitted hits by row id
			engine.correct(row.copy(id = i + 1), emptyList()) { hit, _ -> matched.add(hit.name) }
		}
		return matched
	}

	@Test
	fun `an empty query matches everything`() {
		assertEquals(listOf("alpha", "beta"), hits(SearchQuery(), "alpha", "beta"))
	}

	@Test
	fun `the typed text trims itself and the row name`() {
		assertEquals(listOf(" das Haus "), hits(text("  HAUS  "), " das Haus ", "die Schule"))
	}

	@Test
	fun `the typed text is a case-insensitive contains`() {
		assertEquals(listOf("das Haus"), hits(text("HAUS"), "das Haus", "die Schule"))
	}

	@Test
	fun `the typed text matches each parsed variant of a name`() {
		// the queries are no substrings of the raw name, so only the parsed variant
		// can match, and the display parses the same way
		assertEquals(listOf("(I/You smile.)"), hits(text("I smile."), "(I/You smile.)", "die Schule"))
		assertEquals(listOf("(I/You smile.)"), hits(text("You smile."), "(I/You smile.)", "die Schule"))
	}

	@Test
	fun `a typed text is literal even behind an operator sign`() {
		assertEquals(listOf("=abc"), hits(text("=abc"), "=abc", "x"))
	}

	@Test
	fun `a name criterion reads beginnings and ends`() {
		assertEquals(
			listOf("Schule"),
			hits(nameCriterion(FilterOperator.STARTS, "Schule").query(), "Schule", "die Schule"),
		)
		assertEquals(
			listOf("Schule"),
			hits(nameCriterion(FilterOperator.ENDS, "ule").query(), "Schule", "Schulhof"),
		)
	}

	@Test
	fun `a name criterion keeps case until asked otherwise`() {
		val rows = arrayOf("Dog", "dog")
		assertEquals(listOf("Dog"), hits(nameCriterion(FilterOperator.STARTS, "D").query(), *rows))
		assertEquals(
			listOf("Dog"),
			hits(nameCriterion(FilterOperator.CONTAINS_CASE, "Dog").query(), *rows),
		)
		assertEquals(listOf("Dog", "dog"), hits(text("dog"), *rows))
	}

	@Test
	fun `the exact-case contains keeps the name whitespace`() {
		val spaced = nameCriterion(FilterOperator.CONTAINS_CASE, " w ").query()
		assertEquals(listOf(" w "), hits(spaced, " w ", "w"))
	}

	@Test
	fun `a type criterion keeps one kind`() {
		val rows = arrayOf(item("slovo", ItemKind.WORD), item("kapitola", ItemKind.CHAPTER))
		val words = Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.WORD)).query()
		assertEquals(listOf("slovo"), answers(words, *rows))
		val chapters = Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.CHAPTER)).query()
		assertEquals(listOf("kapitola"), answers(chapters, *rows))
	}

	@Test
	fun `a description criterion reads the description only`() {
		val rows = arrayOf(item("w", description = "a note"), item("e", description = ""))
		val note = Criterion(SearchTag.DESCRIPTION, FilterOperator.CONTAINS, CriterionValue.Text("note")).query()
		assertEquals(listOf("w"), answers(note, *rows))
	}

	@Test
	fun `a description criterion trims the stored description`() {
		val note = Criterion(SearchTag.DESCRIPTION, FilterOperator.CONTAINS, CriterionValue.Text("note")).query()
		assertEquals(listOf("w"), answers(note, item("w", description = " note ")))
	}

	@Test
	fun `the exact-case contains keeps the description whitespace`() {
		val note = Criterion(SearchTag.DESCRIPTION, FilterOperator.CONTAINS_CASE,
			CriterionValue.Text(" note ")).query()
		val rows = arrayOf(item("w", description = " note "), item("e", description = "note"))
		assertEquals(listOf("w"), answers(note, *rows))
	}

	@Test
	fun `every part of a query must hold`() {
		val rows = arrayOf(
			item("Haus", ItemKind.WORD, "old"),
			item("Haus", ItemKind.CHAPTER, "old"),
			item("Haus", ItemKind.WORD, "new"),
		)
		val query = SearchQuery(
			text = "hau",
			criteria = listOf(
				Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.WORD)),
				Criterion(SearchTag.DESCRIPTION, FilterOperator.STARTS, CriterionValue.Text("ol")),
			),
		)
		assertEquals(listOf("Haus"), answers(query, *rows))
	}

	@Test
	fun `a pattern that does not compile matches nothing`() {
		val broken = Criterion(SearchTag.NAME, FilterOperator.REGEX, CriterionValue.Text("[")).query()
		assertEquals(emptyList<String>(), answers(broken, item("x")))
	}

	@Test
	fun `a pattern matches the whole name`() {
		val pattern = Criterion(SearchTag.NAME, FilterOperator.REGEX, CriterionValue.Text("[=]a.c")).query()
		assertEquals(listOf("=abc"), answers(pattern, item("=abc"), item("x")))
	}
}
