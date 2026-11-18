package cz.litoj.schlmgr.unit.ui.popup

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * - the OK press hands the view model an immutable copy of the edited fields.
 * - the copy is taken at press time.
 * - later edits of the still-open dialog cannot change a committed result.
 * - every field of the copy is taken per row, the removals included.
 * - dialogs register on the shared stack on construction.
 * - the teardown clears the stack, so none leak across tests.
 */
class CreatorResultTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `later edits do not change a taken snapshot`() {
		val source = DbItem(id = 7, name = "house", description = "", type = ItemKind.TRANSLATION)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Word(listOf(
			TranslationRow("house", "the building", source),
		)))
		popup.name = "first"
		popup.description = "a noun"
		popup.removedTranslations.add(source)

		val result = popup.result()

		assertEquals("first", result.name)
		assertEquals("a noun", result.description)
		assertEquals("house", result.translations.single().name)
		assertEquals("the building", result.translations.single().description)
		assertEquals(listOf(source), result.removedTranslations)

		popup.name = "second"
		popup.description = "changed later"
		popup.translations[0].name = "edited after"
		popup.removedTranslations.clear()

		assertEquals("the name is the one that was pressed", "first", result.name)
		assertEquals("the description is the one that was pressed", "a noun", result.description)
		assertEquals("each row is copied, not aliased", "house", result.translations.single().name)
		assertEquals("the removals are copied too", listOf(source), result.removedTranslations)
	}
}
