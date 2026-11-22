package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.GatheredRows
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a clumping host swaps a held container for its whole view and back.
 * - an unclumping host keeps the picks exactly as the gestures left them.
 * - only the direct parent of the shown view ever swaps, never a higher ancestor.
 */
class GatheredRowsTest {

	private fun row(id: Int, name: String, kind: ItemKind) =
		ItemRow(DbItem(id = id, name = name, description = "", type = kind), null, 1, false)

	private fun childUnder(id: Int, parent: DbItem) = childUnder(id, "w$id", parent)

	private fun childUnder(id: Int, name: String, parent: DbItem) = ItemRow.ofSearchHit(
		DbItem(id = id, name = name, description = "", type = ItemKind.WORD),
		listOf(parent), 1, emptyList(),
	)

	private val upperItem = DbItem(id = 1, name = "upper", description = "", type = ItemKind.CHAPTER)
	private val chapterItem = DbItem(id = 10, name = "chapter", description = "", type = ItemKind.CHAPTER)
	private val upper = row(1, "upper", ItemKind.CHAPTER)
	private val chapter = row(10, "chapter", ItemKind.CHAPTER)
	private val other = row(11, "other", ItemKind.CHAPTER)
	private val view = listOf(
		childUnder(20, "w1", chapterItem),
		childUnder(21, "w2", chapterItem),
		childUnder(22, "w3", chapterItem),
	)
	private val parentView = listOf(chapter, other)

	private class FakeHost : GatheredRows.Host {
		var shown: List<ItemRow> = emptyList()
		var container: ItemRow? = null
		var clumps = true
		override val models get() = shown
		override fun openContainer() = container
		override fun clumpsContainer() = clumps
		override fun batchChanged() {}
	}

	/** One gather over one list, driven the way the list drives it. */
	private class Scene(clumps: Boolean = true) {
		val host = FakeHost().apply { this.clumps = clumps }
		private val gathered = GatheredRows(host)

		/** The list takes on [rows] as the whole view of [container]. */
		fun show(container: ItemRow?, rows: List<ItemRow>) {
			host.container = container
			host.shown = rows
			gathered.viewShown(rows)
		}

		/** The list leaves the view it holds for another container. */
		fun leave() = gathered.viewLeft(host.shown)

		/** A row added to the shown list, the path that never swaps. */
		fun added(row: ItemRow) {
			host.shown = host.shown + row
			gathered.markShown(listOf(row))
		}

		fun hold(row: ItemRow, on: Boolean) = gathered.hold(row, on)

		fun holdAll() = gathered.toggleAll()

		fun rows() = gathered.rows

		fun picks() = gathered.rows.map { it.item.name }

		fun marks(rows: List<ItemRow>) = rows.map { "${it.item.name}=${it.batched}" }
	}

	@Test
	fun `a held chapter ticks its whole view on open`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)

		scene.leave()
		scene.show(chapter, view)

		assertEquals(listOf("w1", "w2", "w3"), scene.picks())
		assertEquals(listOf("w1=true", "w2=true", "w3=true"), scene.marks(view))
	}

	@Test
	fun `one untick inside the view keeps the rest and drops the chapter`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.leave()
		scene.show(chapter, view)

		scene.hold(view[1], false)

		assertEquals(listOf("w1", "w3"), scene.picks())
		assertEquals(listOf("w1=true", "w2=false", "w3=true"), scene.marks(view))
	}

	@Test
	fun `a wholly held view collapses into its chapter on leave`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.leave()
		scene.show(chapter, view)

		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("chapter"), scene.picks())
		assertEquals(listOf("chapter=true", "other=false"), scene.marks(parentView))
	}

	@Test
	fun `a partly held view leaves its rows as the picks`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.leave()
		scene.show(chapter, view)
		scene.hold(view[2], false)

		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("w1", "w2"), scene.picks())
		assertEquals(listOf("chapter=false", "other=false"), scene.marks(parentView))
	}

	@Test
	fun `rows ticked by hand collapse into the chapter on leave`() {
		val scene = Scene()
		scene.show(chapter, view)
		view.forEach { scene.hold(it, true) }

		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("chapter"), scene.picks())
	}

	@Test
	fun `select all in the view collapses into the chapter on leave`() {
		val scene = Scene()
		scene.show(chapter, view)
		scene.hold(view[0], true)

		scene.holdAll()
		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("chapter"), scene.picks())
	}

	@Test
	fun `an open chapter that is not held marks nothing`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(other, true)

		scene.leave()
		scene.show(chapter, view)

		assertEquals("the sibling pick of the parent view survives", listOf("other"), scene.picks())
		assertEquals(listOf("w1=false", "w2=false", "w3=false"), scene.marks(view))
	}

	@Test
	fun `a held ancestor of the view never marks its rows`() {
		val scene = Scene()
		scene.show(null, listOf(upper))
		scene.hold(upper, true)

		scene.leave()
		scene.show(chapter, view)

		assertEquals(listOf("upper"), scene.picks())
		assertEquals(listOf("w1=false", "w2=false", "w3=false"), scene.marks(view))
	}

	@Test
	fun `showing the same view again changes nothing`() {
		val scene = Scene()
		scene.show(chapter, view)
		view.forEach { scene.hold(it, true) }

		scene.show(chapter, view)

		assertEquals(listOf("w1", "w2", "w3"), scene.picks())
	}

	@Test
	fun `a row added to the view of a held chapter joins no pick`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.leave()
		scene.show(chapter, view)

		scene.added(row(23, "w4", ItemKind.WORD))

		assertEquals(listOf("w1", "w2", "w3"), scene.picks())
	}

	@Test
	fun `an empty view takes in nothing`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)

		scene.leave()
		scene.show(chapter, emptyList())

		assertEquals("the chapter stays the pick", listOf("chapter"), scene.picks())
	}

	@Test
	fun `the picks of another folder survive both swaps`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.hold(other, true)
		scene.leave()
		scene.show(chapter, view)

		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("other", "chapter"), scene.picks())
	}

	@Test
	fun `an unclumped host keeps the chapter pick when its view shows`() {
		val scene = Scene(clumps = false)
		scene.show(null, parentView)
		scene.hold(chapter, true)

		scene.leave()
		scene.show(chapter, view)

		assertEquals(listOf("chapter"), scene.picks())
		assertEquals(listOf("w1=false", "w2=false", "w3=false"), scene.marks(view))
	}

	@Test
	fun `an unclumped host never collapses a wholly held view`() {
		val scene = Scene(clumps = false)
		scene.show(chapter, view)
		view.forEach { scene.hold(it, true) }

		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("w1", "w2", "w3"), scene.picks())
		assertFalse("the chapter stayed unheld", chapter.batched)
	}

	@Test
	fun `an unclumped host marks its rows the same way`() {
		val scene = Scene(clumps = false)
		scene.show(chapter, view)

		scene.hold(view[1], true)

		assertEquals(listOf("w2"), scene.picks())
		assertTrue(view[1].batched)
	}

	@Test
	fun `a view with no container row never swaps`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.hold(other, true)

		scene.leave()

		assertEquals(listOf("chapter", "other"), scene.picks())
	}

	@Test
	fun `a wholly held root view never collapses`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.holdAll()

		scene.leave()

		assertEquals(listOf("chapter", "other"), scene.picks())
	}

	@Test
	fun `expansion keeps the held child and takes in the missing ones`() {
		val scene = Scene()
		val heldChild = childUnder(20, upperItem)
		val shown = listOf(childUnder(20, chapterItem), childUnder(21, chapterItem), childUnder(22, chapterItem))
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.hold(heldChild, true)

		scene.leave()
		scene.show(chapter, shown)

		assertEquals(listOf("w20", "w21", "w22"), scene.picks())
		assertEquals(listOf(1, 10, 10), scene.rows().map { it.parentId })
	}

	@Test
	fun `collapse preserves children picked under another parent`() {
		val scene = Scene()
		val foreignChild = childUnder(20, upperItem)
		val shown = listOf(childUnder(20, chapterItem), childUnder(21, chapterItem))
		scene.show(null, parentView)
			scene.hold(chapter, true)
		scene.hold(foreignChild, true)

		scene.leave()
		scene.show(chapter, shown)
		scene.leave()
		scene.show(null, parentView)

		assertEquals(listOf("w20", "chapter"), scene.picks())
		assertEquals(listOf(1), scene.rows().filter { it.item.id == 20 }.map { it.parentId })
	}

	@Test
	fun `held empty view collapses once after its inserted row is ticked`() {
		val scene = Scene()
		scene.show(null, parentView)
		scene.hold(chapter, true)
		scene.leave()
		scene.show(chapter, emptyList())
		val added = childUnder(23, chapterItem)
		scene.added(added)
		scene.hold(added, true)

		scene.leave()

		assertEquals(listOf("chapter"), scene.picks())
	}
}
