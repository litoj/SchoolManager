package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a browse load takes its reference facts from one preloaded link set.
 * - a default-flipped word takes its translations from one preloaded map.
 * - building the rows must touch no database.
 */
class BrowseRowPreloadTest {

	private val defFlipWas = ItemRow.defFlip

	@After
	fun restore() {
		ItemRow.defFlip = defFlipWas
	}

	private fun chapter(id: Int, name: String) =
		DbItem(id = id, name = name, description = "desc", type = ItemKind.CHAPTER)

	@Test
	fun `a referenced row with another parent is a reference with a source`() {
		val rows = ItemRow.convert(listOf(chapter(2, "c")), 1,
			listOf(DbLink(1, 2, isReference = true), DbLink(9, 2)), emptyMap())
		assertEquals(1, rows.size)
		assertTrue(rows.single().isReference)
		assertTrue(rows.single().hasSource)
	}

	@Test
	fun `a plain row is neither a reference nor a jump`() {
		val rows = ItemRow.convert(listOf(chapter(2, "c")), 1, listOf(DbLink(1, 2)), emptyMap())
		assertFalse(rows.single().isReference)
		assertFalse(rows.single().hasSource)
	}

	@Test
	fun `a reference without another parent has no jump`() {
		val rows = ItemRow.convert(listOf(chapter(2, "c")), 1,
			listOf(DbLink(1, 2, isReference = true)), emptyMap())
		assertTrue(rows.single().isReference)
		assertFalse(rows.single().hasSource)
	}

	@Test
	fun `preloaded rows keep position order and the item display`() {
		val rows = ItemRow.convert(
			listOf(chapter(2, "b"), chapter(3, "a")), 1,
			listOf(DbLink(1, 2), DbLink(1, 3)), emptyMap(),
		)
		assertEquals(listOf(1, 2), rows.map { it.position })
		assertEquals(listOf("b", "a"), rows.map { it.toShow })
		assertEquals(listOf("desc", "desc"), rows.map { it.info })
	}

	@Test
	fun `a default-flipped word renders from the preloaded translations`() {
		ItemRow.defFlip = true
		val word = DbItem(id = 2, name = "Haus", description = "", type = ItemKind.WORD)
		val trl = DbItem(id = 9, name = "dům", description = "budova", type = ItemKind.TRANSLATION)

		val rows = ItemRow.convert(listOf(word), 1,
			listOf(DbLink(1, 2)), mapOf(2 to listOf(trl)))

		val row = rows.single()
		assertTrue("the word took the default flip", row.flipped)
		assertEquals("dům", row.toShow)
		assertEquals("no joint description — the line carries its own", "", row.info)
		assertEquals(listOf("budova"), row.translationLines.map { it.description })
	}

	@Test
	fun `a whole word batch builds flipped without a database`() {
		ItemRow.defFlip = true
		// every word row renders its translations from the map, while a per-row query
		// would throw here since no repository is initialized
		val words = (1..20).map { DbItem(id = it, name = "w$it", description = "", type = ItemKind.WORD) }
		val translations = words.associate {
			it.id to listOf(DbItem(id = 1000 + it.id, name = "t${it.id}", description = "", type = ItemKind.TRANSLATION))
		}

		val rows = ItemRow.convert(words, 1, words.map { DbLink(1, it.id) }, translations)

		assertEquals(20, rows.size)
		assertTrue("every word took the default flip", rows.all { it.flipped })
		assertEquals(listOf("t1", "t2"), rows.take(2).map { it.toShow })
	}
}
