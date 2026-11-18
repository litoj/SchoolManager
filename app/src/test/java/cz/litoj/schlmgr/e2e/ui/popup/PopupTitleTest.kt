package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.popup.PopupHelpIcon
import cz.litoj.schlmgr.ui.popup.PopupTitle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the title band is a floor, so a long title keeps its second line whole.
 * - a trailing icon holds one tap slot at the row end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PopupTitleTest {

	@get:Rule
	val rule = createComposeRule()

	private val narrowCard = 200.dp

	@Test
	fun `the title band is a floor and not a cap`() {
		// Robolectric stubs text metrics, so a tall slot states the growth a wrapped title needs
		val tall = 80.dp
		rule.setContent {
			Box(Modifier.width(narrowCard)) {
				PopupTitle("Filtr hledání") { Box(Modifier.size(tall).testTag("tall slot")) }
			}
		}

		val grown = rule.onNodeWithTag("tall slot").getBoundsInRoot()

		rule.runOnIdle {
			assertTrue("the band holds content taller than one slot", grown.bottom - grown.top >= tall)
		}
	}

	@Test
	fun `a trailing icon holds one tap slot at the row end`() {
		rule.setContent {
			Box(Modifier.width(narrowCard)) {
				PopupTitle("Filtr hledání") { PopupHelpIcon {} }
			}
		}

		val slot = rule.onNodeWithContentDescription("Help").getBoundsInRoot()

		rule.runOnIdle {
			assertTrue("the slot is one tap slot wide", slot.right - slot.left >= 50.dp)
			assertTrue("the slot is one tap slot tall", slot.bottom - slot.top >= 50.dp)
			assertTrue("the slot sits at the row end", slot.left >= narrowCard - 50.dp)
		}
	}
}
