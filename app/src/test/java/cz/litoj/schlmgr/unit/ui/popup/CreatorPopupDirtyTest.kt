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

class CreatorPopupDirtyTest {

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `a fresh dialog is clean`() {
		assertFalse(CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple).isDirty)
	}

	@Test
	fun `renaming dirties the dialog until reverted`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.name = "other"
		assertTrue(popup.isDirty)
		popup.name = ""
		assertFalse(popup.isDirty)
	}

	@Test
	fun `editing the description dirties the dialog`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.description = "a description"
		assertTrue(popup.isDirty)
	}

	@Test
	fun `adding a translation dirties a word dialog`() {
		val existing = TranslationRow("Haus", "dům",
			DbItem(name = "Haus", description = "dům", type = ItemKind.WORD))
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Word(listOf(existing)))
		assertFalse(popup.isDirty)
		popup.translations.add(TranslationRow("", "", null))
		assertTrue(popup.isDirty)
	}
}
