package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

	@Test
	fun `the swap button stays out of an edit popup`() {
		val popup = CreatorPopup(CreatorPopup.Kind.EDIT, null, CreatorContent.Word(emptyList()))
		rule.setContent { popup.Content() }

		rule.onNodeWithContentDescription("Swap word and translations").assertDoesNotExist()
	}
}
