package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests of the word editor's Enter chain — the word name leads to the
 * first translation, a translation's name to the next, and the last one
 * confirms (the OK button's commit) instead of adding a row. An accepted
 * Enter commit of a new word resets the editor for the next one; every
 * other accepted commit closes the popup.
 *
 * Dialogs register on the shared stack on construction and the teardown
 * clears it, so none leak across tests.
 */
class CreatorPopupEnterChainTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup(kind: CreatorPopup.Kind = CreatorPopup.Kind.NEW_WORD) =
		CreatorPopup(kind, null, CreatorContent.Word(emptyList()))

	@Test
	fun `enter in the last translation's name confirms with the current fields`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var committed: CreatorResult? = null
		popup.setOkListener { committed = it; true }

		popup.onTranslationNameEnter(0)

		assertEquals("Haus", committed?.name)
		assertEquals("dům", committed?.translations?.single()?.name)
	}

	@Test
	fun `enter in the last translation's name adds no row`() {
		val popup = wordPopup()
		popup.setOkListener { true }

		popup.onTranslationNameEnter(0)

		assertEquals(1, popup.translations.size)
	}

	@Test
	fun `enter in an earlier translation's name confirms nothing`() {
		val popup = wordPopup()
		popup.translations.add(TranslationRow("stavba", "", null))
		var committed = false
		popup.setOkListener { committed = true; true }

		popup.onTranslationNameEnter(0)

		assertFalse(committed)
		assertEquals(2, popup.translations.size)
	}

	@Test
	fun `a refused enter commit leaves the fields intact`() {
		val popup = wordPopup()
		popup.name = "Haus"
		var commits = 0
		popup.setOkListener { commits++; false }

		popup.onTranslationNameEnter(0)

		assertEquals(1, commits)
		assertEquals("Haus", popup.name)
	}

	@Test
	fun `an accepted enter commit of a new word resets the editor for the next one`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.description = "a noun"
		popup.translations[0].name = "dům"
		popup.setOkListener { true }

		popup.onTranslationNameEnter(0)

		assertEquals("", popup.name)
		assertEquals("", popup.description)
		assertEquals(1, popup.translations.size)
		assertEquals("", popup.translations[0].name)
		assertFalse("the reset editor is clean again", popup.isDirty)
		assertTrue("the run continues — the popup stays shown", Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `the reset editor commits the next word through the same listener`() {
		val popup = wordPopup()
		popup.name = "first"
		popup.translations[0].name = "první"
		val committed = ArrayList<CreatorResult>()
		popup.setOkListener { committed.add(it); true }

		popup.onTranslationNameEnter(0)
		popup.name = "second"
		popup.translations[0].name = "druhá"
		popup.onTranslationNameEnter(0)

		assertEquals(listOf("first", "second"), committed.map { it.name })
	}

	@Test
	fun `an enter commit of an edit popup closes it`() {
		val popup = wordPopup(CreatorPopup.Kind.EDIT)
		popup.setOkListener { true }

		popup.onTranslationNameEnter(0)

		assertFalse(Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `an ok-button commit closes the popup`() {
		val popup = wordPopup()
		popup.setOkListener { true }

		popup.notifyOk()

		assertFalse(Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `an ok-button commit is one shot`() {
		val popup = wordPopup()
		var commits = 0
		popup.setOkListener { commits++; true }

		popup.notifyOk()
		popup.notifyOk()

		assertEquals(1, commits)
	}

	@Test
	fun `enter in the word name confirms nothing`() {
		val popup = wordPopup()
		var committed = false
		popup.setOkListener { committed = true; true }

		popup.onWordNameEnter()

		assertFalse(committed)
		assertEquals(1, popup.translations.size)
	}

	@Test
	fun `enter in the word name adds the row back when none exists`() {
		val popup = wordPopup()
		popup.translations.clear()
		popup.setOkListener { true }

		popup.onWordNameEnter()

		assertEquals(1, popup.translations.size)
	}
}
