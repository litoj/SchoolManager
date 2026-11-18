package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
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
 * - the last empty translation description commits with the fields as they stand.
 * - a refused commit leaves the fields and the run alone.
 * - blank translation rows are discarded before the commit.
 * - an existing translation dropped by the commit joins the edit removals.
 * - an accepted Enter commit of a new word renews the editor with a fresh one.
 * - the renewed editor keeps the listener for the rest of the run.
 * - every other accepted commit closes the popup, the OK button always.
 * - the OK listener is asked once per accepted commit.
 * - dialogs register on the shared stack on construction.
 * - the teardown clears the stack, so none leak across tests.
 */
class CreatorPopupCommitTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup(kind: CreatorPopup.Kind = CreatorPopup.Kind.NEW_WORD) =
		CreatorPopup(kind, null, CreatorContent.Word(emptyList()))

	@Test
	fun `enter in the last translation's empty description confirms with the current fields`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var committed: CreatorResult? = null
		popup.setOkListener { committed = it; true }

		popup.onDescriptionEnter(0)

		assertEquals("Haus", committed?.name)
		assertEquals("dům", committed?.translations?.single()?.name)
	}

	@Test
	fun `a refused enter commit leaves the fields intact`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var commits = 0
		popup.setOkListener { commits++; false }

		popup.onDescriptionEnter(0)

		assertEquals(1, commits)
		assertEquals("Haus", popup.name)
	}

	@Test
	fun `ok discards blank translation rows when actual text remains`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations.add(TranslationRow("dům", "", null))
		popup.translations.add(TranslationRow(" \t ", "", null))
		var committed: CreatorResult? = null
		popup.setOkListener { committed = it; true }

		popup.notifyOk()

		assertEquals(listOf("dům"), committed?.translations?.map { it.name })
		assertEquals(listOf("dům"), popup.translations.map { it.name })
	}

	@Test
	fun `a discarded existing translation joins the edit removals`() {
		val original = DbItem(id = 7, name = "empty", description = "", type = ItemKind.TRANSLATION)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Word(listOf(
			TranslationRow(" \t ", "", original),
			TranslationRow("dům", "", null),
		)))
		popup.name = "Haus"
		var committed: CreatorResult? = null
		popup.setOkListener { committed = it; true }

		popup.notifyOk()

		assertEquals(listOf(original), committed?.removedTranslations)
		assertEquals(listOf("dům"), committed?.translations?.map { it.name })
	}

	@Test
	fun `an accepted enter commit of a new word renews the editor with a fresh one`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.description = "a noun"
		popup.translations[0].name = "dům"
		val committedRow = popup.translations[0]
		popup.setOkListener { true }

		popup.onDescriptionEnter(0)

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

		popup.onDescriptionEnter(0)
		popup.name = "second"
		popup.translations[0].name = "druhá"
		popup.onDescriptionEnter(0)

		assertEquals(listOf("first", "second"), committed.map { it.name })
	}

	@Test
	fun `an enter commit of an edit popup closes it`() {
		val popup = wordPopup(CreatorPopup.Kind.EDIT)
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.setOkListener { true }

		popup.onDescriptionEnter(0)

		assertFalse(Dialogs.stack.value.contains(popup))
	}

	@Test
	fun `an ok-button commit closes the popup`() {
		val closers = mapOf(
			"a new word" to {
				wordPopup().apply {
					name = "Haus"
					translations[0].name = "dům"
				}
			},
			"a chapter" to {
				CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
					.apply { name = "Chapter" }
			},
		)

		for ((case, open) in closers) {
			val popup = open()
			popup.setOkListener { true }

			popup.notifyOk()

			assertFalse("$case ends the run with the OK button", Dialogs.stack.value.contains(popup))
		}
	}

	@Test
	fun `an ok-button commit is one shot`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.name = "Chapter"
		var commits = 0
		popup.setOkListener { commits++; true }

		popup.notifyOk()
		popup.notifyOk()

		assertEquals(1, commits)
	}

	@Test
	fun `enter in the word description adds the row back when none exists`() {
		val popup = wordPopup()
		popup.translations.clear()
		popup.setOkListener { true }

		popup.onDescriptionEnter(null)

		assertEquals(1, popup.translations.size)
	}
}
