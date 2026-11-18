package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.KeptTranslation
import cz.litoj.schlmgr.ui.screen.subjects.splitRows
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.TranslationValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the editor's name field carries the split, the source row marks what it replaces.
 * - a replaced row leaves no copy beside its parts.
 */
class SplitRowsTest {

	@Test
	fun `a split of an existing translation replaces its row`() {
		val trl = DbItem(id = 7, name = "abc", description = "", type = ItemKind.TRANSLATION)
		// the editor turned the one "abc" row into two parts, keeping its description
		val result = CreatorResult("w", "", listOf(TranslationValue("xyz\\def", "old desc", trl)), emptyList())

		val split = splitRows(result, editing = true)

		assertEquals("no part stays on the old row", emptyList<KeptTranslation>(), split?.first)
		assertEquals("the edited row is the one it drops", listOf(trl.id), split?.second)
		assertEquals("the first part carries the description, the next starts empty",
			listOf("xyz" to "old desc", "def" to null), split?.third)
	}
}
