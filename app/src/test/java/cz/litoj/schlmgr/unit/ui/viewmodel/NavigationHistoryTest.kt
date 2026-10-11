package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NavigationHistory]'s recorded hop kinds — a descent hop grows the path and its
 * back pops it, a search run leaves the path alone. The explorer's back navigation
 * reads a run carried by the position it returns to ([NavigationHistory.onSearchHop]) to restore
 * the filtered list — so these kinds must survive the open/back round trip.
 */
class NavigationHistoryTest {

	private fun item(id: Int, name: String = "i$id") =
		DbItem(id = id, name = name, description = "", type = ItemKind.CHAPTER)

	private fun pathOf(vararg items: DbItem): MutableList<DbItem> {
		val path = mutableListOf<DbItem>()
		items.forEach { path.add(it) }
		return path
	}

	@Test
	fun `a descent hop grows the path and its back pops it`() {
		val history = NavigationHistory()

		history.hop(item(1))

		assertEquals(1, history.path.size)
		assertTrue("a same-path hop pops itself", history.remove())
		assertEquals(0, history.path.size)
	}

	@Test
	fun `a new-path hop stores the previous path and restores it on back`() {
		val history = NavigationHistory()
		val subject = item(1)
		val chapter = item(2)
		history.hop(subject)

		history.push(pathOf(subject, chapter))

		assertEquals(2, history.path.size)
		assertFalse(history.remove())
		assertEquals(1, history.path.size)
		assertEquals(subject.id, history.path.lastOrNull()?.id)
	}

	@Test
	fun `a search run leaves the path and reports a search hop`() {
		val history = NavigationHistory()
		history.hop(item(1))

		history.hop(null)

		assertEquals(1, history.path.size)
		assertTrue(history.onSearchHop())

		// leaving the search unwinds only the search hop, not the descent below it
		history.removeSearchHops()
		assertEquals(1, history.path.size)
		assertFalse(history.onSearchHop())
	}

	@Test
	fun `a back hop onto a search run reports a search hop`() {
		val history = NavigationHistory()
		val subject = item(1)
		val chapter = item(2)
		history.hop(subject) // open the subject
		history.hop(null) // search inside it
		history.push(pathOf(subject, chapter)) // open a hit's container

		assertFalse("the opened container is not a search", history.onSearchHop())

		history.remove() // back out of the container

		assertEquals(1, history.path.size)
		assertTrue("the position returned to carries the search run", history.onSearchHop())
	}

	@Test
	fun `onSearchHop is false on a plain browse path`() {
		val history = NavigationHistory()
		history.hop(item(1))

		assertFalse(history.onSearchHop())
	}

	@Test
	fun `a sibling switch replaces the current entry, so back pops a level up`() {
		val history = NavigationHistory()
		val subject = item(1)
		val chapterX = item(2)
		val chapterY = item(3)
		history.hop(subject) // open the subject
		history.push(pathOf(subject, chapterX)) // open its chapter

		history.replaceCurrent(pathOf(subject, chapterY)) // the sibling swipe

		assertEquals(chapterY.id, history.path.lastOrNull()?.id)
		assertFalse("back leaves the whole switched entry", history.remove())
		assertEquals("back lands on the parent, not the pre-switch sibling",
			subject.id, history.path.lastOrNull()?.id)
	}
}
