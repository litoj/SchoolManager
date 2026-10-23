package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - the OK press hands the view model an immutable copy of the edited fields.
 * - the copy is taken at press time.
 * - later edits of the still-open dialog cannot change a committed result.
 * - dialogs register on the shared stack on construction.
 * - the teardown clears the stack, so none leak across tests.
 */
class CreatorResultTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `the snapshot copies the current fields`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Simple)
		popup.name = "das Haus"
		popup.description = "podstatné jméno"

		val result = popup.result()

		assertEquals("das Haus", result.name)
		assertEquals("podstatné jméno", result.description)
	}

	@Test
	fun `later edits do not change a taken snapshot`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Simple)
		popup.name = "first"
		val result = popup.result()

		popup.name = "second"
		popup.description = "changed later"

		assertEquals("first", result.name)
		assertEquals("", result.description)
	}

	@Test
	fun `a word dialog's translations are copied per row`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))
		// a fresh word editor starts with one empty row
		popup.translations[0].name = "house"
		popup.translations[0].description = "the building"

		val result = popup.result()

		assertEquals(1, result.translations.size)
		assertEquals("house", result.translations.single().name)
		assertEquals("the building", result.translations.single().description)
		assertTrue(result.removedTranslations.isEmpty())

		popup.translations[0].name = "edited after"
		assertEquals("house", result.translations.single().name)
	}

	@Test
	fun `removed translations travel with the snapshot`() {
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Simple)
		val source = DbItem(
			id = 7, name = "house", description = "", type = ItemKind.TRANSLATION)
		popup.removedTranslations.add(source)

		val result = popup.result()
		assertFalse(result.removedTranslations.isEmpty())
		assertEquals(7, result.removedTranslations.single().id)
	}
}
