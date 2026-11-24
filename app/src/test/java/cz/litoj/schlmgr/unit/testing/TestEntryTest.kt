package cz.litoj.schlmgr.unit.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TestEntryTest {

	@Test
	fun `an injected translation snapshot needs no repository`() {
		val word = DbItem(name = "word", description = "", type = ItemKind.WORD)
		val translation = DbItem(name = "translation", description = "", type = ItemKind.TRANSLATION)
		val entry = TestEntry(word, listOf(word), listOf(translation))

		assertEquals(listOf(translation), entry.translations())
	}
}
