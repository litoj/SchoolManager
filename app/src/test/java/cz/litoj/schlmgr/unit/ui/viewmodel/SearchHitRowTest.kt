package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of [ItemRow.ofSearchHit]: a search-hit row takes its reference facts
 * (`isReference` / `hasSource`) from the preloaded parent links instead of
 * per-row repository queries — building the row must touch no database.
 */
class SearchHitRowTest {

	private val parent = DbItem(id = 1, name = "ch", description = "", type = ItemKind.CHAPTER)

	private val word = DbItem(id = 2, name = "w", description = "", type = ItemKind.WORD)

	@Test
	fun `a referenced hit row with another parent is a reference with a source`() {
		val row = ItemRow.ofSearchHit(
			word, listOf(parent), 1,
			listOf(DbLink(1, 2, isReference = true), DbLink(9, 2)),
		)
		assertTrue(row.isReference)
		assertTrue(row.hasSource)
	}

	@Test
	fun `a plain hit row is neither a reference nor a jump`() {
		val row = ItemRow.ofSearchHit(word, listOf(parent), 1, listOf(DbLink(1, 2)))
		assertFalse(row.isReference)
		assertFalse(row.hasSource)
	}

	@Test
	fun `a reference without another parent has no jump`() {
		val row = ItemRow.ofSearchHit(word, listOf(parent), 1, listOf(DbLink(1, 2, isReference = true)))
		assertTrue(row.isReference)
		assertFalse(row.hasSource)
	}

	@Test
	fun `a non-reference link from another parent is not a reference`() {
		// the row was found under parent 9, whose link is not a reference
		val row = ItemRow.ofSearchHit(word, listOf(parent), 1, listOf(DbLink(9, 2)))
		assertFalse(row.isReference)
		assertFalse(row.hasSource)
	}

	@Test
	fun `the hit row carries the path as a copy and the given position`() {
		val path = ArrayList<DbItem>()
		path.add(parent)
		val row = ItemRow.ofSearchHit(word, path, 7, listOf(DbLink(1, 2)))
		assertEquals(7, row.position)
		assertEquals(listOf<DbItem>(parent), row.path)
		path.clear()
		// the walk mutates its path list — the row must own its copy
		assertEquals(1, row.path?.size)
		assertNotSame(path, row.path)
	}
}
