package cz.litoj.schlmgr.e2e.ui.popup

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.DialogScrim
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogOutsideTouchTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun popup() =
		CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))

	private fun header() = ApplicationProvider.getApplicationContext<Context>()
		.getString(R.string.new_word)

	@Test
	fun `a tap on the card surface outside its fields keeps the dialog`() {
		val popup = popup()
		rule.setContent { DialogScrim(popup) }

		rule.onNodeWithText(header()).performClick()

		rule.runOnIdle { assertTrue(Dialogs.isShowing<CreatorPopup>()) }
	}
}
