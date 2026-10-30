package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the word name leads to the first translation.
 * - the name of a translation leads to the next one.
 * - the last translation confirms through the commit of the OK button.
 * - an accepted confirm of a new word renews the editor for the next one.
 * - the OK button closes, like every other accepted commit.
 * - dialogs register on the shared stack on construction.
 * - the teardown clears the stack, so none leak across tests.
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
	fun `an accepted enter commit of a new word renews the editor with a fresh one`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.description = "a noun"
		popup.translations[0].name = "dům"
		val committedRow = popup.translations[0]
		popup.setOkListener { true }

		popup.onTranslationNameEnter(0)

		assertEquals("", popup.name)
		assertEquals("", popup.description)
		assertEquals(1, popup.translations.size)
		assertEquals("", popup.translations[0].name)
		assertNotSame("the renewal builds a fresh editor, not a wiped one", committedRow, popup.translations[0])
		assertFalse("the renewed editor is clean again", popup.isDirty)
		assertTrue("the run continues, the popup stays shown", Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `the renewed editor commits the next word through the same listener`() {
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
	fun `an ok-button commit of a new word closes it`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.setOkListener { true }

		popup.notifyOk()

		assertFalse("the OK button never renews, the run ends", Dialogs.stack.value.contains(popup))
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
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.setOkListener { true }

		popup.notifyOk()

		assertFalse(Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `an ok-button commit is one shot`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
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
