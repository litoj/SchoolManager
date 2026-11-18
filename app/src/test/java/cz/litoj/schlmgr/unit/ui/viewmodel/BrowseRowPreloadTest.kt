package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a browse load builds its rows from one preloaded link set and one translations map.
 * - building the rows must touch no database.
 */
class BrowseRowPreloadTest {

	private val defFlipWas = ItemRow.defFlip

	@After
	fun restore() {
		setRowOptions(defFlip = defFlipWas)
	}

	private fun chapter(id: Int, name: String) =
		DbItem(id = id, name = name, description = "desc", type = ItemKind.CHAPTER)

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
		setRowOptions(defFlip = true)
		val word = DbItem(id = 2, name = "Haus", description = "", type = ItemKind.WORD)
		val trl = DbItem(id = 9, name = "dům", description = "budova", type = ItemKind.TRANSLATION)

		val row = ItemRow.convert(listOf(word), 1,
			listOf(DbLink(1, 2)), mapOf(2 to listOf(trl))).single()

		assertTrue("the word took the default flip", row.flipped)
		assertEquals("dům", row.toShow)
		assertEquals("no joint description, the line carries its own", "", row.info)
		assertEquals(listOf("budova"), row.translationLines.map { it.description })

		// a whole list renders the same way, and no repository exists to read from
		val words = (1..20).map { DbItem(id = it, name = "w$it", description = "", type = ItemKind.WORD) }
		val translations = words.associate {
			it.id to listOf(DbItem(id = 1000 + it.id, name = "t${it.id}", description = "", type = ItemKind.TRANSLATION))
		}

		val batch = ItemRow.convert(words, 1, words.map { DbLink(1, it.id) }, translations)

		assertEquals(20, batch.size)
		assertTrue("every word took the default flip", batch.all { it.flipped })
		assertEquals(listOf("t1", "t2"), batch.take(2).map { it.toShow })
	}
}
