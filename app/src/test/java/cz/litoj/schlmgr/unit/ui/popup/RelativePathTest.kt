package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.relativeTo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the chain starts at the chapter the search runs in, not at the subject root.
 * - a chain from outside the subject of the search shows whole.
 */
class RelativePathTest {

	private fun chain(vararg names: String) = names.map {
		DbItem(name = it, description = "", type = ItemKind.CHAPTER)
	}

	private fun names(chain: List<DbItem>) = chain.map { it.name }

	@Test
	fun `a subject-level search shows the descent below the subject`() {
		val path = chain("A", "c1")
		assertEquals(listOf("c1"), names(path.relativeTo(chain("A"))))
	}

	@Test
	fun `a deeper search drops the levels down to its chapter`() {
		val path = chain("A", "c1", "c2")
		assertEquals(listOf("c2"), names(path.relativeTo(chain("A", "c1"))))
	}

	@Test
	fun `the location equal to the searched chapter shows itself`() {
		val path = chain("A", "c1")
		assertEquals(listOf("c1"), names(path.relativeTo(chain("A", "c1"))))
	}

	@Test
	fun `a chain outside the search's subject shows whole`() {
		val path = chain("B", "c2")
		assertEquals(listOf("B", "c2"), names(path.relativeTo(chain("A", "c1"))))
	}

	@Test
	fun `an empty origin shows the whole chain`() {
		val path = chain("B", "c2")
		assertEquals(listOf("B", "c2"), names(path.relativeTo(emptyList())))
	}
}
