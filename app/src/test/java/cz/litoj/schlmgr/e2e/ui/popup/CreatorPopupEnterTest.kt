package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the Enter key commits on the last name field in the view.
 * - that is the item name for the simple kinds.
 * - for words it is the name of the last translation.
 * - the word name and the earlier translations move the keyboard along.
 * - a note is the exception, where Enter jumps to the description.
 * - its commit stays on the OK button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CreatorPopupEnterTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup() =
		CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))

	@Test
	fun `enter in a chapter's name confirms`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.name = "Kapitel eins"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Kapitel eins").performImeAction()

		rule.runOnIdle {
			assertTrue(committed)
			assertFalse(Dialogs.stack.value.contains(popup))
		}
	}

	@Test
	fun `enter in a note's name moves the keyboard to the description`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		popup.name = "Einkäufe"
		popup.description = "die Liste"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Einkäufe").performImeAction()

		rule.runOnIdle { assertFalse(committed) }
		rule.onNodeWithText("die Liste").assertIsFocused()
	}

	@Test
	fun `enter in an edited note's name moves the keyboard to the description`() {
		val row = ItemRow(
			DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, row, CreatorContent.Simple)
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("alt").performImeAction()

		rule.runOnIdle { assertFalse(committed) }
		rule.onNodeWithText("alte Notiz").assertIsFocused()
	}

	@Test
	fun `enter in the word name moves the keyboard to the first translation`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Haus").performImeAction()

		rule.runOnIdle {
			assertFalse(committed)
			assertEquals(1, popup.translations.size)
		}
		rule.onNodeWithText("dům").assertIsFocused()
	}

	@Test
	fun `enter in an earlier translation's name moves the keyboard to the next`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations.add(TranslationRow("stavba", "", null))
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performImeAction()

		rule.runOnIdle { assertFalse(committed) }
		rule.onNodeWithText("stavba").assertIsFocused()
	}

	@Test
	fun `enter in the last translation's name confirms and renews the editor`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performImeAction()

		rule.runOnIdle {
			assertTrue(committed)
			assertEquals(1, popup.translations.size)
			assertEquals("the editor stands ready for the next word", "", popup.name)
		}
	}

	/** The field holding the keyboard: typing lands where this matcher points. */
	private val focusedField = SemanticsMatcher("focused field") {
		it.config.getOrElse(SemanticsProperties.Focused) { false }
	}

	@Test
	fun `the renewed editor takes typing in its fresh fields`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.setOkListener { true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performImeAction()

		// the keyboard landed in the fresh name field, so typing reaches it
		rule.onNode(focusedField).performTextInput("Baum")
		rule.runOnIdle { assertEquals("Baum", popup.name) }
		// and the Enter chain continues into the fresh translation row
		rule.onNode(focusedField).performImeAction()
		rule.onNode(focusedField).performTextInput("strom")
		rule.runOnIdle { assertEquals("strom", popup.translations[0].name) }
	}
}
