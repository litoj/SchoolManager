package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipConflict
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.FlipConflictPopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the commit maps left, edited and removed rows to their resolutions.
 * - edits must dirty the dialog, or a close would drop them unseen.
 * - a refused verdict keeps the dialog open, so the user can retry.
 * - the teardown clears the shared stack, so no dialog leaks across tests.
 */
class FlipConflictPopupTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	/** Distinct ids let the kept and removed lists prove row identity, not just names. */
	private var nextId = 1

	private fun conflict(name: String) = FlipConflict(
		DbItem(id = nextId++, name = name, description = "", type = ItemKind.TRANSLATION),
		outside = emptyList(), scheduled = emptyList())

	/** Runs the ok and returns the committed resolutions. */
	private fun commit(popup: FlipConflictPopup): List<FlipResolution> {
		var out: List<FlipResolution>? = null
		popup.setOkListener { out = it; true }
		popup.notifyOk()
		return out!!
	}

	@Test
	fun `an untouched row keeps its contents`() {
		val popup = FlipConflictPopup(listOf(conflict("pes")))
		val resolutions = commit(popup)

		val kept = resolutions.filter { !it.removed }
		assertEquals("pes", kept.single().name)
		assertEquals(1, kept.single().rowId)
		assertTrue(resolutions.none { it.removed })
	}

	@Test
	fun `an edited row carries the custom contents`() {
		val popup = FlipConflictPopup(listOf(conflict("pes")))
		popup.rows.single().name = "pes "
		popup.rows.single().description = "custom"
		val kept = commit(popup).filter { !it.removed }.single()

		assertEquals("pes ", kept.name)
		assertEquals("custom", kept.description)
	}

	@Test
	fun `a removed row leaves the kept list for the removed one`() {
		val pes = conflict("pes")
		val dog = conflict("dog")
		val popup = FlipConflictPopup(listOf(pes, dog))
		popup.remove(popup.rows.first())
		val resolutions = commit(popup)

		assertEquals(listOf("dog"), resolutions.filter { !it.removed }.map { it.name })
		assertEquals(listOf("pes"), resolutions.filter { it.removed }.map { it.name })
		assertEquals(pes.row.id, resolutions.single { it.removed }.rowId)
	}

	@Test
	fun `edits and removals dirty the dialog, an untouched one stays clean`() {
		assertFalse(FlipConflictPopup(listOf(conflict("pes"))).isDirty)

		val edited = FlipConflictPopup(listOf(conflict("pes")))
		edited.rows.single().name += " "
		assertTrue(edited.isDirty)

		val removed = FlipConflictPopup(listOf(conflict("pes"), conflict("dog")))
		removed.remove(removed.rows.last())
		assertTrue(removed.isDirty)
	}

	@Test
	fun `a refused verdict keeps the dialog open, an accepted one dismisses it`() {
		val popup = FlipConflictPopup(listOf(conflict("pes")))
		popup.setOkListener { false }
		assertFalse(popup.notifyOk())
		assertTrue(Dialogs.isShowing<FlipConflictPopup>())

		popup.setOkListener { true }
		assertTrue(popup.notifyOk())
		assertFalse(Dialogs.isShowing<FlipConflictPopup>())
	}

	@Test
	fun `a second ok after acceptance commits nothing`() {
		val popup = FlipConflictPopup(listOf(conflict("pes")))
		val commits = ArrayList<List<FlipResolution>>()
		popup.setOkListener { commits.add(it); true }
		popup.notifyOk()

		// the closing commit is one shot. a double tap must not commit twice
		assertTrue(popup.notifyOk())
		assertEquals(1, commits.size)
	}
}
