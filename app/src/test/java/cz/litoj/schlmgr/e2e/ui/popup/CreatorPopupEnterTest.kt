package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
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
 * The word editor's Enter chain on the composed popup — the word name and
 * the earlier translations move the keyboard along the fields, the last
 * translation's name confirms (the OK button's commit) instead of adding
 * a row.
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
	fun `enter in the last translation's name confirms and resets the editor`() {
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
}
