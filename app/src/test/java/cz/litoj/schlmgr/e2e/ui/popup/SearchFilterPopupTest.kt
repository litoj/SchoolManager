package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.SearchFilterPopup
import cz.litoj.schlmgr.ui.search.FilterCondition
import cz.litoj.schlmgr.ui.search.FilterField
import cz.litoj.schlmgr.ui.search.FilterType
import cz.litoj.schlmgr.ui.search.SearchFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The labeled controls of the search filter, which own the written query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchFilterPopupTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun show(query: String, onApply: (SearchFilter?) -> Unit) {
		SearchFilterPopup(query, onApply)
		rule.setContent {
			val stack by Dialogs.stack.collectAsState()
			stack.forEach { it.Content() }
		}
	}

	@Test
	fun `the controls build a number filter`() {
		var applied: SearchFilter? = null
		show("") { applied = it }

		rule.onNodeWithText("Words").performClick()
		rule.onNodeWithText("Success rate").performClick()
		rule.onNodeWithText("is more than").performClick()
		rule.onNode(hasSetTextAction()).performTextInput("50")
		rule.onNodeWithText("Query: \\WR>50").assertExists()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle {
			assertEquals(
				SearchFilter(FilterType.WORD, FilterField.RATE, FilterCondition.GREATER, "50"),
				applied,
			)
		}
	}

	@Test
	fun `a non-number value keeps apply inactive`() {
		var applied: SearchFilter? = null
		show("") { applied = it }

		rule.onNodeWithText("Success rate").performClick()
		rule.onNode(hasSetTextAction()).performTextInput("abc")
		rule.onNodeWithText("Enter a whole number.").assertExists()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle { assertNull(applied) }
	}

	@Test
	fun `a written query reopens on its filter`() {
		show("\\WDsdog") {}

		rule.onNodeWithText("Words").assertExists()
		rule.onNodeWithText("Description").assertExists()
		rule.onNodeWithText("starts with").assertExists()
		rule.onNodeWithText("dog").assertExists()
	}
}
