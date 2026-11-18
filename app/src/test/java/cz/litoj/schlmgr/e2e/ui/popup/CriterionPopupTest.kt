package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.popup.CriterionPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchQuery
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The labeled controls of one search filter.
 * - the controls own the criterion, so nothing here is written down and read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CriterionPopupTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun show(
		query: SearchQuery = SearchQuery(),
		existing: Criterion? = null,
		onApply: (Criterion) -> Unit = {},
		onDrop: (SearchTag) -> Unit = {},
	) {
		CriterionPopup(query, existing, onApply, onDrop)
		rule.setContent {
			val stack by Dialogs.stack.collectAsState()
			stack.forEach { it.Content() }
		}
	}

	private fun criterion(tag: SearchTag, operator: FilterOperator, value: CriterionValue) =
		Criterion(tag, operator, value)

	@Test
	fun `a new filter starts on the kind tag`() {
		var applied: Criterion? = null
		show(onApply = { applied = it })

		rule.onNodeWithText("Filter by").assertExists()
		rule.onNodeWithText("Words").assertExists()
		rule.onNodeWithText("Value").assertDoesNotExist()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle {
			assertEquals(criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.WORD)), applied)
		}
	}

	@Test
	fun `the rate filter names its bound and its untested rows`() {
		var applied: Criterion? = null
		show(onApply = { applied = it })

		rule.onNodeWithText("Success rate").performClick()
		rule.onNodeWithText("Right answers as a percent of all answers.").assertExists()
		rule.onNodeWithText("Which rows").assertDoesNotExist()
		rule.onNodeWithText("is at most").performScrollTo().performClick()
		rule.onNode(hasSetTextAction()).performTextInput("50")
		rule.onNodeWithText("Success rate is at most 50").assertExists()
		rule.onNodeWithText("stay out").performClick()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle {
			assertEquals(
				criterion(SearchTag.RATE, FilterOperator.AT_MOST, CriterionValue.Rate(50, UntestedRows.EXCLUDE)),
				applied,
			)
		}
	}

	@Test
	fun `only the rate asks about the untested rows`() {
		var applied: Criterion? = null
		show(onApply = { applied = it })

		rule.onNodeWithText("Times tested").performScrollTo().performClick()
		rule.onNodeWithText("Untested rows").assertDoesNotExist()
		rule.onNodeWithText("All answers together, zero means no test yet.").assertExists()
		rule.onNode(hasSetTextAction()).performTextInput("0")
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle {
			assertEquals(criterion(SearchTag.TESTED, FilterOperator.GREATER, CriterionValue.Times(0)), applied)
		}
	}

	@Test
	fun `the text tags offer the text comparisons only`() {
		show()

		rule.onNodeWithText("Name").performScrollTo().performClick()
		rule.onNodeWithText("starts with").assertExists()
		rule.onNodeWithText("is at most").assertDoesNotExist()
		rule.onNodeWithText("Untested rows").assertDoesNotExist()
	}

	@Test
	fun `a value that does not fit the tag holds the filter back`() {
		var applied: Criterion? = null
		show(onApply = { applied = it })

		rule.onNodeWithText("Success rate").performClick()
		rule.onNode(hasSetTextAction()).performTextInput("abc")
		rule.onNodeWithText("Enter a whole number.").assertExists()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle { assertNull("nothing applied", applied) }
	}

	@Test
	fun `an empty text value holds the filter back`() {
		var applied: Criterion? = null
		show(onApply = { applied = it })

		rule.onNodeWithText("Description").performScrollTo().performClick()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle {
			assertNull("nothing applied", applied)
			rule.onNodeWithText("Enter a value.").assertExists()
		}
	}

	@Test
	fun `an open filter reopens on its choices`() {
		var dropped: SearchTag? = null
		val open = criterion(SearchTag.RATE, FilterOperator.AT_MOST, CriterionValue.Rate(50, UntestedRows.EXCLUDE))
		show(SearchQuery(criteria = listOf(open)), open, onDrop = { dropped = it })

		rule.onNodeWithText("Filter by").assertDoesNotExist()
		rule.onNodeWithText("is at most").assertExists()
		rule.onNodeWithText("stay out").assertExists()
		rule.onNodeWithText("50").assertExists()
		rule.onNodeWithText("Clear").performClick()

		rule.runOnIdle { assertEquals(SearchTag.RATE, dropped) }
	}

	@Test
	fun `picking a tag back loads the filter it already has`() {
		var applied: Criterion? = null
		val name = criterion(SearchTag.NAME, FilterOperator.STARTS, CriterionValue.Text("dog"))
		show(SearchQuery(criteria = listOf(name)), onApply = { applied = it })

		rule.onNodeWithText("Name").performScrollTo().performClick()
		rule.onNodeWithText("dog").assertExists()
		rule.onNodeWithText("Apply").performClick()

		rule.runOnIdle { assertEquals(name, applied) }
	}
}
