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

/**
 * - a word whose translations hold no name never commits.
 * - its blank rows are dropped, one empty row stays ready.
 * - a refused word sends the keyboard to the first field still missing.
 * - a note holding no body never commits, a new one and an emptied one alike.
 * - a note marks its description required.
 * - the note with its body and the simple item without a description both commit.
 * - the teardown clears the dialog stack, so none leak across tests.
 */
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

	/** A stored note, the row an edit popup opens on. */
	private fun editedNote() = ItemRow(
		DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)

	@Test
	fun `ok discards blank rows and refuses a word without names`() {
		val popup = wordPopup()
		var commits = 0
		popup.setOkListener { commits++; false }
		popup.name = "Haus"
		popup.translations.add(TranslationRow(" \t ", "", null))
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle {
			assertEquals("a missing name never reaches the commit", 0, commits)
			assertEquals("the discarded rows leave one required translation", 1, popup.translations.size)
			assertEquals("the row kept is the empty one put back", "", popup.translations.single().name)
		}
	}

	/** One composition per case: the rule takes content once per test. */
	private fun refusalParksTheKeyboardIn(missing: Missing) {
		val popup = wordPopup()
		popup.name = missing.name
		popup.translations[0].name = missing.translation
		rule.setContent { popup.Content() }
		// the open parks the keyboard in the name field, so the refusal has to move it
		rule.onNodeWithText(missing.heldText).performClick()

		rule.onNodeWithText("OK").performClick()
		rule.waitForIdle()

		// typing reaches the field the refusal parked the keyboard in
		rule.onNode(focusedField).performTextInput("!")
		rule.runOnIdle {
			assertEquals("${missing.case} took the typing", "!", missing.landedIn(popup))
		}
	}

	@Test
	fun `a refused word with a filled translation focuses the empty name`() {
		refusalParksTheKeyboardIn(Missing("a name still missing", "", "dům", "dům") { name })
	}

	@Test
	fun `a refused word with a name focuses the empty translation`() {
		refusalParksTheKeyboardIn(
			Missing("a translation still missing", "Haus", "", "Haus") { translations[0].name })
	}

	/** One composition per case: the rule takes content once per test. */
	private fun emptyNoteRefusesCommit(popup: CreatorPopup, case: String) {
		var commits = 0
		popup.setOkListener { commits++; false }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals("$case never commits an empty body", 0, commits) }
	}

	@Test
	fun `ok refuses a note opened without a body`() {
		emptyNoteRefusesCommit(
			CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
				.apply { name = "Note" },
			"a note opened empty",
		)
	}

	@Test
	fun `ok refuses an edited note emptied of its body`() {
		emptyNoteRefusesCommit(
			CreatorPopup(CreatorPopup.Kind.EDIT, editedNote(), CreatorContent.Simple)
				.apply { description = "" },
			"an edited note emptied of its body",
		)
	}

	/** One composition per case: the rule takes content once per test. */
	private fun emptyNoteMarksDescriptionRequired(popup: CreatorPopup) {
		rule.setContent { popup.Content() }

		// an edit opens holding its body, the marker belongs to the empty one
		popup.description = ""
		rule.onNodeWithText("Description *").assertIsDisplayed()
	}

	@Test
	fun `a new note marks its description required`() {
		emptyNoteMarksDescriptionRequired(
			CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple))
	}

	@Test
	fun `an edited note marks its description required`() {
		emptyNoteMarksDescriptionRequired(
			CreatorPopup(CreatorPopup.Kind.EDIT, editedNote(), CreatorContent.Simple))
	}

	/** One composition per case: the rule takes content once per test. */
	private fun completeFieldsCommitOnce(kind: CreatorPopup.Kind, description: String, case: String) {
		val popup = CreatorPopup(kind, null, CreatorContent.Simple)
		var ok = 0
		popup.setOkListener { ok++; true }
		popup.name = "Item"
		popup.description = description
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals("$case commits once", 1, ok) }
	}

	@Test
	fun `a note holding its body commits`() {
		completeFieldsCommitOnce(CreatorPopup.Kind.NEW_NOTE, "the body", "a note holding its body")
	}

	@Test
	fun `a chapter holds no description and still commits`() {
		completeFieldsCommitOnce(CreatorPopup.Kind.NEW_CHAPTER, "", "a chapter holding no description")
	}

	/** One refusal of a word: its fields, the text holding the keyboard first, the field to reach. */
	private class Missing(
		val case: String,
		val name: String,
		val translation: String,
		val heldText: String,
		val landedIn: CreatorPopup.() -> String,
	)

	/** The field holding the keyboard: typing lands where this matcher points. */
	private val focusedField = SemanticsMatcher("focused field") {
		it.config.getOrElse(SemanticsProperties.Focused) { false }
	}
}
