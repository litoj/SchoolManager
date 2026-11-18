package cz.litoj.schlmgr.e2e.ui.popup

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import cz.litoj.schlmgr.R
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - every item opens with the keyboard in its name field, a new one and an edit alike.
 * - the open consumes the keyboard wish, so a rebuilt composition raises no new one.
 * - a name Enter leads to the item description, whatever the kind.
 * - an empty description leads to the first or the next translation name.
 * - a translation name Enter leads to its own description.
 * - the Enter of the last empty description confirms and renews the editor.
 * - the Done key of a simple item's empty description commits it.
 * - a filled description takes a newline instead of the action.
 * - a field keeps its keyboard options while the user types.
 * - the add button scrolls to the bottom and focuses the new row.
 * - removing a row hands the keyboard to the previous one.
 * - a removal asks for the keyboard once its field dies.
 * - a deleted row keeps the rows scroll where it is.
 * - a return of the window focus with a field focused reopens the keyboard.
 * - the teardown clears the dialog stack, so none leak across tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CreatorPopupKeyboardTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup() =
		CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))

	/** A stored note, the row an edit popup opens on. */
	private fun editedNote() = ItemRow(
		DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)

	/** One composition per case: the rule takes content once per test. */
	private fun opensWithNameFocus(popup: CreatorPopup, nameText: String, case: String) {
		assertTrue("$case holds its keyboard wish until the open", popup.wantsNameFocus)
		rule.setContent { popup.Content() }

		// the name field holds the stored text of an edit, and the typed one of a new item
		rule.onNodeWithText(nameText).assertIsFocused()
		assertTrue("$case opens with the keyboard", keyboardShown())
		assertFalse("$case loses the wish at the open", popup.wantsNameFocus)
	}

	@Test
	fun `a new item opens with the keyboard in its name field`() {
		opensWithNameFocus(wordPopup().apply { name = "Haus" }, "Haus", "a new word")
	}

	@Test
	fun `an edited item opens with the keyboard in its stored name`() {
		opensWithNameFocus(
			CreatorPopup(CreatorPopup.Kind.EDIT, editedNote(), CreatorContent.Simple),
			"alt", "an edited note",
		)
	}

	/** Owns the lifecycle the popup observes, so the test drives the app return by hand. */
	private class TestOwner : LifecycleOwner {
		val registry = LifecycleRegistry.createUnsafe(this)
		override val lifecycle: Lifecycle get() = registry
	}

	@Test
	fun `a return to the app with a field focused reopens the keyboard`() {
		val popup = wordPopup()
		popup.name = "Haus"
		val owner = TestOwner()
		owner.registry.currentState = Lifecycle.State.RESUMED
		rule.setContent {
			CompositionLocalProvider(LocalLifecycleOwner provides owner) {
				popup.Content()
			}
		}
		rule.onNodeWithText("Haus").assertIsFocused()
		assertTrue(keyboardShown())

		// the system hides the keyboard when the window loses focus
		owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		imm().hideSoftInputFromWindow(null, 0)
		rule.runOnIdle { assertFalse(keyboardShown()) }

		owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
		rule.runOnIdle { assertTrue(keyboardShown()) }
	}

	@Test
	fun `the add button scrolls to the bottom and focuses the new row`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null,
			CreatorContent.Word((1..6).map { TranslationRow("t$it", "", null) }))
		rule.setContent { popup.Content() }
		rule.waitForIdle()

		// the rows fill the middle section, so the add button starts below the fold.
		// the semantic scroll reaches it, a swipe on the fields scrolls nothing
		rule.onNodeWithContentDescription(addWordLabel()).performScrollTo()
		rule.waitForIdle()
		rule.onNodeWithContentDescription(addWordLabel()).performClick()
		rule.waitForIdle()

		assertEquals(7, popup.translations.size)
		// the new row holds the keyboard, so typing reaches its name field
		rule.onNode(focusedField).performTextInput("x")
		rule.runOnIdle { assertEquals("x", popup.translations.last().name) }
		// and the row sits at the visible bottom
		rule.onNode(focusedField).assertIsDisplayed()
	}

	private fun addWordLabel(): String =
		ApplicationProvider.getApplicationContext<Context>().getString(R.string.add_word)

	/** One composition per case: the rule takes content once per test. */
	private fun deleteARowHandsOver(focusTheRow: Boolean, case: String) {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations.add(TranslationRow("stavba", "", null))
		rule.setContent { popup.Content() }
		rule.waitForIdle()
		if (focusTheRow) {
			rule.onNodeWithText("stavba").performClick()
			rule.waitForIdle()
		}
		assertTrue("$case starts with the keyboard up", keyboardShown())

		// the cross of the second of the two rows, the keyboard may have pushed it down
		rule.onAllNodesWithContentDescription("Delete")[1].performScrollTo().performClick()
		rule.waitForIdle()

		assertEquals("$case leaves one row behind", 1, popup.translations.size)
		// the previous row's name field holds the keyboard, so typing reaches it
		rule.onNode(focusedField).performTextInput("!")
		rule.runOnIdle {
			assertEquals("$case hands the typing to the previous row", "dům!", popup.translations[0].name)
		}
		assertTrue("$case keeps the keyboard up", keyboardShown())
	}

	@Test
	fun `removing a focused translation hands the keyboard to the previous row`() {
		deleteARowHandsOver(focusTheRow = true, case = "a focused row")
	}

	@Test
	fun `removing an unfocused translation hands the keyboard to the previous row`() {
		deleteARowHandsOver(focusTheRow = false, case = "a row nobody was in")
	}

	@Test
	fun `a deletion asks for the keyboard after its field dies`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations.add(TranslationRow("stavba", "", null))
		rule.setContent { popup.Content() }
		rule.waitForIdle()
		rule.onNodeWithText("stavba").performClick()
		rule.waitForIdle()
		assertTrue(keyboardShown())
		imm().hideSoftInputFromWindow(null, 0)
		rule.runOnIdle { assertFalse(keyboardShown()) }

		rule.onAllNodesWithContentDescription("Delete")[1].performClick()
		rule.waitForIdle()

		assertTrue("the keyboard is asked for after the removal", keyboardShown())
		rule.onNode(focusedField).performTextInput("!")
		rule.runOnIdle { assertEquals("dům!", popup.translations[0].name) }
	}

	@Test
	fun `a deleted row keeps the rows scroll where it is`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null,
			CreatorContent.Word((1..20).map { TranslationRow("t$it", "", null) }))
		rule.setContent { popup.Content() }
		rule.waitForIdle()

		// twenty rows overflow the middle section, so the end sits below the fold
		rule.onNodeWithText("t20").assertIsNotDisplayed()
		rule.onAllNodesWithContentDescription("Delete")[1].performScrollTo().performClick()
		rule.waitForIdle()

		assertEquals(19, popup.translations.size)
		// the removal is no append, the view keeps its place
		rule.onNodeWithText("t20").assertIsNotDisplayed()
	}

	/** Reads the input manager, the only view of the keyboard state. */
	private fun imm(): InputMethodManager =
		ApplicationProvider.getApplicationContext<Context>()
			.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

	/** One composition per case: the rule takes content once per test. */
	private fun enterNameMovesToDescription(popup: CreatorPopup, descriptionText: String, case: String) {
		val nameText = popup.name
		val rows = popup.translations.size
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText(nameText).performImeAction()

		rule.runOnIdle {
			assertFalse("$case never commits on a name Enter", committed)
			assertEquals("$case gains no row from a name Enter", rows, popup.translations.size)
		}
		rule.onNodeWithText(descriptionText).assertIsFocused()
	}

	@Test
	fun `enter in a note name moves the keyboard to its description`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		popup.name = "Einkäufe"
		popup.description = "die Liste"
		enterNameMovesToDescription(popup, "die Liste", "a note")
	}

	@Test
	fun `enter in an edited note name moves the keyboard to its description`() {
		enterNameMovesToDescription(
			CreatorPopup(CreatorPopup.Kind.EDIT, editedNote(), CreatorContent.Simple),
			"alte Notiz", "an edited note",
		)
	}

	@Test
	fun `enter in a word name moves the keyboard to its description`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.description = "a noun"
		popup.translations[0].name = "dům"
		enterNameMovesToDescription(popup, "a noun", "a word")
	}

	@Test
	fun `enter in an empty word description moves to the first translation`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onAllNodes(hasSetTextAction())[1].performImeAction()

		rule.runOnIdle {
			assertFalse(committed)
			assertEquals("the chain reaches the row that is there already", 1, popup.translations.size)
		}
		rule.onNodeWithText("dům").assertIsFocused()
	}

	@Test
	fun `enter in a translation name moves the keyboard to its description`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations[0].description = "first description"
		popup.translations.add(TranslationRow("stavba", "", null))
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performImeAction()

		rule.runOnIdle { assertFalse(committed) }
		rule.onNodeWithText("first description").assertIsFocused()
	}

	@Test
	fun `enter in an earlier empty translation description moves to the next name`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations.add(TranslationRow("stavba", "", null))
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onAllNodes(hasSetTextAction())[3].performImeAction()

		rule.runOnIdle {
			assertFalse("a row below the focused one is never committed", committed)
			assertEquals("the chain adds no row", 2, popup.translations.size)
		}
		rule.onNodeWithText("stavba").assertIsFocused()
	}

	@Test
	fun `enter in an empty simple-item description confirms`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_CHAPTER, null, CreatorContent.Simple)
		popup.name = "Chapter"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onAllNodes(hasSetTextAction())[1].performImeAction()

		rule.runOnIdle {
			assertTrue("the Done action of the empty description commits", committed)
			assertFalse("the accepted commit closes the popup", Dialogs.stack.value.contains(popup))
		}
	}

	@Test
	fun `typing keeps the description text and its keyboard options stable`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Haus").performImeAction()
		val field = rule.onNode(focusedField)
		val initialAction = field.fetchSemanticsNode().config[SemanticsProperties.ImeAction]
		field.performTextInput("a")
		field.performTextInput("b")

		rule.runOnIdle { assertEquals("ab", popup.description) }
		assertEquals(initialAction, field.fetchSemanticsNode().config[SemanticsProperties.ImeAction])
	}

	@Test
	fun `enter in a filled description inserts a newline`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_NOTE, null, CreatorContent.Simple)
		popup.name = "Einkäufe"
		popup.description = "note"
		var committed = false
		popup.setOkListener { committed = true; true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Einkäufe").performImeAction()
		rule.onNodeWithText("note").performImeAction()

		rule.runOnIdle {
			assertEquals("note\n", popup.description)
			assertFalse(committed)
		}
	}

	@Test
	fun `the renewed editor takes typing in its fresh fields`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.setOkListener { true }
		rule.setContent { popup.Content() }

		rule.onNodeWithText("dům").performImeAction()
		rule.onNode(focusedField).performImeAction()

		// the keyboard landed in the fresh name field, so typing reaches it
		rule.onNode(focusedField).performTextInput("Baum")
		rule.runOnIdle { assertEquals("Baum", popup.name) }
		// and the Enter chain continues into the fresh translation row
		rule.onNode(focusedField).performImeAction()
		rule.onNode(focusedField).performImeAction()
		rule.onNode(focusedField).performTextInput("strom")
		rule.runOnIdle { assertEquals("strom", popup.translations[0].name) }
	}

	/** The field holding the keyboard: typing lands where this matcher points. */
	private val focusedField = SemanticsMatcher("focused field") {
		it.config.getOrElse(SemanticsProperties.Focused) { false }
	}

	/** Reads the input manager shadow, since only it sees the keyboard ask. */
	private fun keyboardShown() = shadowOf(imm()).isSoftInputVisible
}
