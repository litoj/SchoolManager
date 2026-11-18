package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CreatorPopupRequiredFieldsTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup() =
		CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))
			.apply { setOkListener { false } }

	@Test
	fun `ok discards blank rows and refuses a word without names`() {
		val popup = wordPopup()
		var commits = 0
		popup.setOkListener { commits++; false }
		popup.translations.add(TranslationRow("", "", null))
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertEquals("a missing name never reaches the commit", 0, commits)
			assertEquals("the discarded rows leave one required translation", 1, popup.translations.size)
		}
	}

	@Test
	fun `ok restores a translation row after its last row was deleted`() {
		val popup = wordPopup()
		var commits = 0
		popup.setOkListener { commits++; false }
		popup.name = "Haus"
		popup.translations.clear()
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertEquals("the missing translation still refuses the commit", 0, commits)
			assertEquals(1, popup.translations.size)
		}
	}

	@Test
	fun `a refused word with a filled translation focuses the empty name`() {
		val popup = wordPopup()
		popup.translations[0].name = "dům"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performClick()
		rule.onNodeWithText("OK").performClick()
		rule.waitForIdle()

		rule.onNode(focusedField).performTextInput("Haus")
		rule.runOnIdle { assertEquals("Haus", popup.name) }
	}

	@Test
	fun `a refused word with a name focuses the empty translation`() {
		val popup = wordPopup()
		popup.name = "Haus"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()
		rule.waitForIdle()

		rule.onNode(focusedField).performTextInput("dům")
		rule.runOnIdle { assertEquals("dům", popup.translations[0].name) }
	}

	@Test
	fun `ok refuses a note that holds no description`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		var commits = 0
		popup.setOkListener { commits++; false }
		popup.name = "Note"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals("the note body never commits empty", 0, commits) }
	}

	@Test
	fun `a note marks its description required`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		popup.name = "Note"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Description *").assertIsDisplayed()
	}

	@Test
	fun `an edited note marks its description required`() {
		val row = ItemRow(
			DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, row, CreatorContent.Simple)
		rule.setContent { popup.Content() }

		popup.description = ""
		rule.onNodeWithText("Description *").assertIsDisplayed()
	}

	@Test
	fun `ok refuses an edited note whose description was emptied`() {
		val row = ItemRow(
			DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, row, CreatorContent.Simple)
		var commits = 0
		popup.setOkListener { commits++; false }
		popup.description = ""
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals("an emptied note body never commits", 0, commits) }
	}

	@Test
	fun `a note with its description commits`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		var commits = 0
		popup.setOkListener { commits++; true }
		popup.name = "Note"
		popup.description = "the body"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals(1, commits) }
	}

	@Test
	fun `a chapter still commits without a description`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		var commits = 0
		popup.setOkListener { commits++; true }
		popup.name = "Chapter"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals(1, commits) }
	}

	/** The field holding the keyboard: typing lands where this matcher points. */
	private val focusedField = SemanticsMatcher("focused field") {
		it.config.getOrElse(SemanticsProperties.Focused) { false }
	}
}
