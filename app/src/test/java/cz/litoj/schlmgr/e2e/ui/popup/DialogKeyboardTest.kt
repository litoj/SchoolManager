package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard of the dialog stack, which the host owns.
 * - the last dialog to leave takes the keyboard with it, so none stays without a field.
 * - a dialog that leaves one below keeps the keyboard for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogKeyboardTest {

	@get:Rule
	val rule = createComposeRule()

	private var hides = 0

	private val keyboard = object : SoftwareKeyboardController {
		override fun show() {}
		override fun hide() { hides++ }
	}

	private inner class Probe(val name: String) : DialogSpec {
		@Composable
		override fun Content() = Text(name)
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun host() {
		rule.setContent {
			CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
				DialogHost()
			}
		}
		rule.waitForIdle()
	}

	private fun show(vararg specs: DialogSpec) {
		rule.runOnIdle { specs.forEach { Dialogs.show(it) } }
		rule.waitForIdle()
	}

	@Test
	fun `a screen with no dialog hides no keyboard`() {
		host()

		rule.runOnIdle { assertEquals(0, hides) }
	}

	@Test
	fun `the last dialog to leave hides the keyboard`() {
		host()
		val one = Probe("one")
		show(one)

		rule.runOnIdle { Dialogs.dismiss(one) }
		rule.waitForIdle()

		rule.runOnIdle { assertEquals(1, hides) }
	}

	@Test
	fun `a dialog leaving one below keeps the keyboard for it`() {
		host()
		val below = Probe("below")
		val above = Probe("above")
		show(below, above)

		rule.runOnIdle { Dialogs.dismiss(above) }
		rule.waitForIdle()

		rule.runOnIdle { assertEquals(0, hides) }
	}

	@Test
	fun `a dialog replaced by another keeps the keyboard`() {
		host()
		val first = Probe("first")
		show(first)

		rule.runOnIdle {
			Dialogs.dismiss(first)
			Dialogs.show(Probe("next"))
		}
		rule.waitForIdle()

		rule.runOnIdle { assertEquals(0, hides) }
	}
}
