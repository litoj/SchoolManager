package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the swap exchanges the word with its translations and their descriptions.
 * - the swap button belongs to a new word, an edit popup never shows it.
 * - the teardown clears the dialog stack, so none leak across tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CreatorPopupSwapTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `the swap button exchanges the word and its translations`() {
		val popup = CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))
		popup.name = "one\\two"
		popup.description = "first desc\\second desc"
		popup.translations[0].name = "first"
		popup.translations[0].description = "first trl"
		popup.translations.add(TranslationRow("second", "second trl", null))
		rule.setContent { popup.Content() }

		rule.onNodeWithContentDescription("Swap word and translations").performClick()

		rule.runOnIdle {
			assertEquals("first\\second", popup.name)
			assertEquals("first trl\\second trl", popup.description)
			assertEquals(listOf("one", "two"), popup.translations.map { it.name })
			assertEquals(listOf("first desc", "second desc"), popup.translations.map { it.description })
		}
	}

	/** One composition per case: the rule takes content once per test. */
	private fun swapNodes(kind: CreatorPopup.Kind): Int {
		val popup = CreatorPopup(kind, null, CreatorContent.Word(emptyList()))
		rule.setContent { popup.Content() }
		return rule.onAllNodesWithContentDescription("Swap word and translations")
			.fetchSemanticsNodes().size
	}

	@Test
	fun `a new word offers the swap`() {
		assertEquals("the new word shows its swap", 1, swapNodes(CreatorPopup.Kind.NEW_WORD))
	}

	@Test
	fun `an edited word offers no swap`() {
		assertEquals("the edit hides the swap", 0, swapNodes(CreatorPopup.Kind.EDIT))
	}
}
