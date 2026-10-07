package cz.litoj.schlmgr.ui

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.explorer.CurrentData.BackLog
import cz.litoj.schlmgr.ui.explorer.CurrentData.EasyList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BackLog]'s recorded hop kinds: a descent hop grows the path and its
 * back pops it, a search run leaves the path alone. The explorer's back navigation
 * reads a run carried by the position it returns to ([BackLog.onSearchHop]) to restore
 * the filtered list — so these kinds must survive the open/back round trip.
 */
class BackLogTest {

	private fun item(id: Int, name: String = "i$id") =
		DbItem(id = id, name = name, description = "", type = ItemKind.CHAPTER)

	private fun pathOf(vararg items: DbItem): EasyList<DbItem> {
		val path = EasyList<DbItem>()
		items.forEach { path.add(it) }
		return path
	}

	@Test
	fun `a descent hop grows the path and its back pops it`() {
		val log = BackLog().apply { clear() }

		log.add(false, item(1), null)

		assertEquals(1, log.path.size)
		assertTrue("a same-path hop pops itself", log.remove())
		assertEquals(0, log.path.size)
	}

	@Test
	fun `a new-path hop stores the previous path and restores it on back`() {
		val log = BackLog().apply { clear() }
		val subject = item(1)
		val chapter = item(2)
		log.add(false, subject, null)

		log.add(true, null, pathOf(subject, chapter))

		assertEquals(2, log.path.size)
		assertFalse(log.remove())
		assertEquals(1, log.path.size)
		assertEquals(subject.id, log.path.getOrNull(-1)?.id)
	}

	@Test
	fun `a search run leaves the path and reports a search hop`() {
		val log = BackLog().apply { clear() }
		log.add(false, item(1), null)

		log.add(false, null, pathOf(item(1)))

		assertEquals(1, log.path.size)
		assertTrue(log.onSearchHop())

		// leaving the search unwinds only the search hop, not the descent below it
		log.removeSearchHops()
		assertEquals(1, log.path.size)
		assertFalse(log.onSearchHop())
	}

	@Test
	fun `a back hop onto a search run reports a search hop`() {
		val log = BackLog().apply { clear() }
		val subject = item(1)
		val chapter = item(2)
		log.add(false, subject, null) // open the subject
		log.add(false, null, pathOf(subject)) // search inside it
		log.add(true, null, pathOf(subject, chapter)) // open a hit's container

		assertFalse("the opened container is not a search", log.onSearchHop())

		log.remove() // back out of the container

		assertEquals(1, log.path.size)
		assertTrue("the position returned to carries the search run", log.onSearchHop())
	}

	@Test
	fun `onSearchHop is false on a plain browse path`() {
		val log = BackLog().apply { clear() }
		log.add(false, item(1), null)

		assertFalse(log.onSearchHop())
	}
}
