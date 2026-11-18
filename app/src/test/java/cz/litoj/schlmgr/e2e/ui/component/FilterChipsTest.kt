package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.FilterChips
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.UntestedRows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chips of the filters a search runs with.
 * - each chip answers to its own filter only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilterChipsTest {

	@get:Rule
	val rule = createComposeRule()

	private val words = Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.WORD))

	private val rate = Criterion(
		SearchTag.RATE, FilterOperator.AT_MOST, CriterionValue.Rate(50, UntestedRows.EXCLUDE),
	)

	@Test
	fun `a chip names its filter in the app language`() {
		rule.setContent { FilterChips(listOf(words, rate), {}, {}) }

		rule.onNodeWithText("Type: Words").assertExists()
		rule.onNodeWithText("Success rate is at most 50 · except untested").assertExists()
	}

	@Test
	fun `a chip label opens its own filter`() {
		var edited: Criterion? = null
		rule.setContent { FilterChips(listOf(words, rate), { edited = it }, {}) }

		rule.onNodeWithText("Type: Words").performClick()

		rule.runOnIdle { assertEquals(words, edited) }
	}

	@Test
	fun `the mark drops its own filter`() {
		var dropped: SearchTag? = null
		rule.setContent { FilterChips(listOf(words, rate), {}, { dropped = it }) }

		rule.onAllNodesWithContentDescription("Remove filter").onLast().performClick()

		rule.runOnIdle { assertEquals(SearchTag.RATE, dropped) }
	}
}
