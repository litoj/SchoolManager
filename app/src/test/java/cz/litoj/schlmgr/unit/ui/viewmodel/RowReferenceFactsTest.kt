package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reference facts of a preloaded row, one rule behind both build doors.
 * - a reference is a fact of the link seen from the parent, not of the item.
 * - only a reference jumps, and only when a second container holds the row.
 */
class RowReferenceFactsTest {

	private val child = DbItem(id = 2, name = "c", description = "desc", type = ItemKind.CHAPTER)
	private val parent = DbItem(id = 1, name = "p", description = "", type = ItemKind.CHAPTER)

	/** Every door takes the same links of the child and answers the same facts. */
	private val doors = mapOf(
		"the browse load" to { links: List<DbLink> ->
			ItemRow.convert(listOf(child), parent.id, links, emptyMap()).single()
		},
		"the search hit" to { links: List<DbLink> ->
			ItemRow.ofSearchHit(child, listOf(parent), 1, links)
		},
	)

	@Test
	fun `the link seen from the parent decides the facts`() {
		val cases = mapOf(
			"a reference of another parent too" to
				(listOf(DbLink(1, 2, isReference = true), DbLink(9, 2)) to (true to true)),
			"a plain link of this parent" to
				(listOf(DbLink(1, 2)) to (false to false)),
			"a reference of this parent only" to
				(listOf(DbLink(1, 2, isReference = true)) to (true to false)),
			"a reference of another parent only" to
				(listOf(DbLink(9, 2, isReference = true)) to (false to false)),
			"a plain link of another parent only" to
				(listOf(DbLink(9, 2)) to (false to false)),
		)

		for ((doorName, door) in doors) {
			for ((case, linksAndFacts) in cases) {
				val (links, facts) = linksAndFacts

				val row = door(links)

				assertEquals("$doorName: $case", facts.first, row.isReference)
				assertEquals("$doorName: $case", facts.second, row.hasSource)
			}
		}
	}
}
