package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - a dialog holding the fields it was opened with is clean.
 * - a change of name, description or rows dirties it.
 * - putting the fields back cleans it again.
 * - the teardown clears the dialog stack, so none leak across tests.
 */
class CreatorPopupDirtyTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	/** A word editor holding one stored translation, the fields every case starts from. */
	private fun editedWord() = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Word(listOf(
		TranslationRow("Haus", "dům",
			DbItem(name = "Haus", description = "dům", type = ItemKind.WORD)),
	)))

	@Test
	fun `a fresh dialog is clean`() {
		assertFalse("a chapter starts clean",
			CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple).isDirty)
		assertFalse("an edited word starts clean", editedWord().isDirty)
	}

	@Test
	fun `any field change dirties the editor`() {
		val changes: List<Triple<String, CreatorPopup.() -> Unit, CreatorPopup.() -> Unit>> = listOf(
			Triple("a renamed item", { name = "other" }, { name = "" }),
			Triple("a rewritten description", { description = "a description" }, { description = "" }),
			Triple("an added translation",
				{ translations.add(TranslationRow("", "", null)) },
				{ translations.removeAt(translations.lastIndex) }),
		)

		for ((case, change, undo) in changes) {
			val popup = editedWord()

			popup.change()
			assertTrue("$case dirties the dialog", popup.isDirty)

			popup.undo()
			assertFalse("$case put back cleans the dialog", popup.isDirty)
		}
	}
}
