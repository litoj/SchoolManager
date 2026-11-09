package cz.litoj.schlmgr.e2e.ui.shell

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.ui.shell.MessageSnackbarHost
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - a new undoable message replaces the shown one, whichever undo the snackbar held.
 * - other messages keep waiting for their turn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageSnackbarTest {

	@get:Rule
	val rule = createComposeRule()

	// the virtual clock runs by hand, or the shown snackbar times out during an idle wait
	@Before
	fun setUp() {
		rule.mainClock.autoAdvance = false
		rule.setContent { MessageSnackbarHost(messageSnackbarHost()) }
		settle()
	}

	private fun settle() {
		rule.mainClock.advanceTimeBy(FRAME_MS)
		rule.waitForIdle()
		rule.mainClock.advanceTimeBy(FADE_MS)
	}

	private fun undoable(name: String, onUndo: () -> Unit = {}) {
		UserMessages.showUndoable(UiNotice.SourceRemoved(name), onUndo)
		settle()
	}

	private fun text(msg: String) {
		UserMessages.showMsg(msg, msg)
		settle()
	}

	@Test
	fun `a new removal replaces the shown one and only it undoes`() {
		var undone = ""
		undoable("first") { undone += "first" }
		rule.onNodeWithText("Removed: first").assertExists()

		undoable("second") { undone += "second" }

		rule.onNodeWithText("Removed: first").assertDoesNotExist()
		rule.onNodeWithText("Removed: second").assertExists()
		rule.onNodeWithText("Undo").performClick()
		settle()
		assertEquals("second", undone)
	}

	@Test
	fun `a removal waiting behind a message is replaced before it shows`() {
		var undone = ""
		text("report")
		undoable("first") { undone += "first" }
		undoable("second") { undone += "second" }
		rule.onNodeWithText("report").assertExists()

		rule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
		settle()

		rule.onNodeWithText("Removed: first").assertDoesNotExist()
		rule.onNodeWithText("Undo").performClick()
		settle()
		assertEquals("second", undone)
	}

	@Test
	fun `a message without an action dismisses on a tap`() {
		text("report")
		rule.onNodeWithText("report").assertExists()

		rule.onNodeWithText("report").performClick()
		settle()

		rule.onNodeWithText("report").assertDoesNotExist()
	}

	@Test
	fun `a message with an action stays on a tap outside the action`() {
		undoable("first")

		rule.onNodeWithText("Removed: first").performClick()
		settle()

		rule.onNodeWithText("Removed: first").assertExists()
	}

	@Test
	fun `a text message queues behind a shown removal`() {
		undoable("first")

		text("report")

		rule.onNodeWithText("Removed: first").assertExists()
		rule.onNodeWithText("report").assertDoesNotExist()
	}

	@Test
	fun `a text message does not replace the shown text`() {
		text("one")

		text("two")

		rule.onNodeWithText("one").assertExists()
		rule.onNodeWithText("two").assertDoesNotExist()
	}

	private companion object {
		const val FRAME_MS = 32L
		const val FADE_MS = 400L
		const val SNACKBAR_SHORT_MS = 4_000L
	}
}
