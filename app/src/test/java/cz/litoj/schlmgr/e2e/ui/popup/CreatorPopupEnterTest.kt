package cz.litoj.schlmgr.e2e.ui.popup

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
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
 * - the Enter key commits on the last name field in the view.
 * - that is the item name for the simple kinds.
 * - for words it is the name of the last translation.
 * - the word name and the earlier translations move the keyboard along.
 * - a note is the exception, where Enter jumps to the description.
 * - its commit stays on the OK button.
 * - every item opens with the keyboard in the name field, a new one and an edit alike.
 * - the keyboard wish is one shot, so a rebuilt composition raises no new keyboard.
 * - a return of the window focus with a field focused reopens the keyboard.
 * - the add button scrolls to the bottom and focuses the new row.
 * - removing a focused translation hands the keyboard to the previous row.
 * - a deleted row keeps the rows scroll where it is.
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
	fun `a new item opens with the keyboard in the name field`() {
		val popup = wordPopup()
		popup.name = "Haus"
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Haus").assertIsFocused()
		assertTrue(keyboardShown())
	}

	@Test
	fun `an edited item opens with the keyboard in its name field`() {
		val row = ItemRow(
			DbItem(id = 5, name = "alt", description = "alte Notiz", type = ItemKind.NOTE), null, 1)
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, row, CreatorContent.Simple)
		rule.setContent { popup.Content() }

		rule.onNodeWithText("alt").assertIsFocused()
		assertTrue(keyboardShown())
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

	@Test
	fun `removing a focused translation hands the keyboard to the previous row`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations[0].name = "dům"
		popup.translations.add(TranslationRow("stavba", "", null))
		rule.setContent { popup.Content() }
		rule.waitForIdle()

		// the keyboard sits in the last row's name field
		rule.onNodeWithText("stavba").performClick()
		rule.waitForIdle()
		assertTrue(keyboardShown())

		// the cross of the focused row, the second of the two rows
		rule.onAllNodesWithContentDescription("Delete")[1].performClick()
		rule.waitForIdle()

		assertEquals(1, popup.translations.size)
		// the previous row's name field holds the keyboard, typing reaches it
		rule.onNode(focusedField).performTextInput("!")
		rule.runOnIdle { assertEquals("dům!", popup.translations[0].name) }
		assertTrue("the keyboard stayed up", keyboardShown())
	}

	@Test
	fun `a deleted row keeps the rows scroll where it is`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null,
			CreatorContent.Word((1..20).map { TranslationRow("t$it", "", null) }))
		rule.setContent { popup.Content() }
		rule.waitForIdle()

		// twenty rows overflow the middle section, so the end sits below the fold
		rule.onNodeWithText("t20").assertIsNotDisplayed()
		rule.onAllNodesWithContentDescription("Delete")[1].performClick()
		rule.waitForIdle()

		assertEquals(19, popup.translations.size)
		// the removal is no append, the view keeps its place
		rule.onNodeWithText("t20").assertIsNotDisplayed()
	}

	/** Reads the input manager, the only view of the keyboard state. */
	private fun imm(): InputMethodManager =
		ApplicationProvider.getApplicationContext<Context>()
			.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

	@Test
	fun `the open consumes the keyboard wish`() {
		val popup = wordPopup()
		popup.name = "Haus"
		assertTrue(popup.wantsNameFocus)
		rule.setContent { popup.Content() }

		rule.onNodeWithText("Haus").assertIsFocused()
		assertFalse(popup.wantsNameFocus)
	}

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

	/** Reads the input manager shadow, since only it sees the keyboard ask. */
	private fun keyboardShown() = shadowOf(imm()).isSoftInputVisible

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
