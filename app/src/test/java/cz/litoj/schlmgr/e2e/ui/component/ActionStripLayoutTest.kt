package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ImportActionBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * One action strip lays its labels out on one line.
 * - a label without a glyph keeps the height of the glyph slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionStripLayoutTest {

	@get:Rule
	val rule = createComposeRule()

	private fun text(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	private fun strip() {
		rule.setContent {
			Box(Modifier.fillMaxSize()) {
				ImportActionBar(
					busy = false,
					imported = false,
					onCancel = {},
					onImport = {},
					onDone = {},
				)
			}
		}
		rule.waitForIdle()
	}

	/** The center line of one label, the button node merges its text otherwise. */
	private fun labelCenter(res: Int): Double {
		val bounds: DpRect = rule.onNodeWithText(text(res), useUnmergedTree = true).getBoundsInRoot()
		return ((bounds.top + bounds.bottom) / 2).value.toDouble()
	}

	@Test
	fun `the import row keeps both labels on one line`() {
		strip()

		// the import action is the one glyph-less button of the bar
		assertEquals("both labels share one center line",
			labelCenter(R.string.objects_cancel), labelCenter(R.string.import_here), 0.5)
	}
}
