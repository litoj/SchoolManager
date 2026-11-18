package cz.litoj.schlmgr.unit.ui.screen.subjects

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.ui.screen.subjects.exportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * - the name reads the selection size and the open container, nothing else.
 * - a selection with no writable row names nothing, so its door stays closed.
 */
class ExportFileNameTest {

	private fun row(name: String) = DbItem(name = name, description = "", type = ItemKind.CHAPTER)

	/** One export: its format, the picked rows, the open container, the file it names. */
	private class Name(val format: ExportFormat, val picked: List<DbItem>, val open: DbItem?, val file: String)

	@Test
	fun `the file name follows the selection size and the open container`() {
		val names = mapOf(
			"a picked chapter" to Name(ExportFormat.WORD, listOf(row("c1")), row("A"), "c1.txt"),
			"two words of the open chapter" to Name(
				ExportFormat.WORD, listOf(row("xyz"), row("w2")), row("c1"), "c1 - partial.txt"),
			"one subject at the root" to Name(ExportFormat.WORD, listOf(row("A")), null, "A.txt"),
			"two subjects at the root" to Name(ExportFormat.WORD, listOf(row("A"), row("B")), null, "subjects.txt"),
			"a chapter as JSON" to Name(ExportFormat.JSON, listOf(row("c1")), row("A"), "c1"),
		)

		for ((case, name) in names)
			assertEquals(case, name.file, exportFileName(name.format, name.picked, name.open))
	}

	@Test
	fun `a selection of nothing gets no file name`() {
		assertNull("an empty selection opens no dialog",
			exportFileName(ExportFormat.WORD, emptyList(), row("A")))
	}
}
