package cz.litoj.schlmgr.e2e.ui.popup

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.ImportFailureExplanation
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.WordListFixPopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * The editor of a failed word list, opened on the spot that failed.
 * - the fixture line lacks its separator, so the caret sits where the fix belongs.
 * - every case rides the failure report, no dialog is built by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordListFixPopupTest {

	@get:Rule
	val rule = createAndroidComposeRule<ComponentActivity>()

	/** the picked document, held for the editor by the pick of the screen */
	private val file = Uri.parse("content://schlmgr.test/list.txt")

	private val written = ByteArrayOutputStream()

	private val reimported = mutableListOf<Uri>()

	private lateinit var popup: WordListFixPopup
	private lateinit var userMessages: UserMessages

	private val field get() = rule.onNode(hasSetTextAction())

	@Before
	fun setUp() {
		userMessages = UserMessages(RuntimeEnvironment.getApplication())
		shadowOf(rule.activity.contentResolver).registerOutputStream(file, written)
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	/** The report of a failed [source] as the worker of the picked [target] sends it. */
	private fun showFix(target: Uri = file, source: String = SOURCE) {
		val failure = parseFailure(source)
		popup = userMessages.onWordListParseFail(failure, target, source, reimported::add)
			as WordListFixPopup
		rule.setContent { popup.Content() }
	}

	private fun parseFailure(source: String): WordListParseException = try {
		WordListReader.parse(source, NAME)
		throw AssertionError("the fixture imports cleanly: $source")
	} catch (e: WordListParseException) {
		e
	}

	private fun text(id: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(id, *args)

	/**
	 * The button a user reads as the confirm: the one at the right edge of the row.
	 * - every popup of the app waits for its action there, the editor included.
	 */
	private fun confirmButton(): SemanticsNodeInteraction {
		val row = rule.onAllNodes(hasText(text(R.string.popup_save_retry)) or hasText(text(R.string.cancel)))
		val buttons = row.fetchSemanticsNodes()
		assertEquals("the editor offers its save and its dismissal", 2, buttons.size)
		return row[buttons.indices.maxByOrNull { buttons[it].boundsInRoot.center.x }!!]
	}

	@Test
	fun `the report of the spot sits above the file it explains`() {
		showFix()

		rule.onNodeWithText(text(R.string.import_error_location, NAME, 1)).assertIsDisplayed()
		rule.onNodeWithText(ImportFailureExplanation.of(parseFailure(SOURCE)).guidance).assertIsDisplayed()
		rule.onNodeWithText(SOURCE).assertIsDisplayed()
		rule.onNodeWithText("↓").assertDoesNotExist()
	}

	@Test
	fun `the editor opens on the character that failed`() {
		showFix()

		field.assertTextEquals(SOURCE)
		rule.onNode(hasSetTextAction() and caretOn(CARET)).assertExists()
	}

	@Test
	fun `the editor opens without the keyboard, so the file stays in view`() {
		showFix()

		rule.onNode(hasSetTextAction() and focused(false)).assertExists()
	}

	@Test
	fun `the report opens with a notice that the file holds an error`() {
		showFix()

		rule.onNodeWithText(text(R.string.popup_file_has_error)).assertIsDisplayed()
	}

	@Test
	fun `the reported line is brought into view`() {
		showFix(source = DEEP)

		// the failing line sits 800 px down a 470 px window, so a reveal moves it far
		rule.onNode(fileScrolledPast(100f)).assertExists()
	}

	@Test
	fun `Save writes the fix into the picked document and imports it again`() {
		showFix()

		field.performTextInput(FIX)
		rule.onNodeWithText(text(R.string.popup_save_retry)).performClick()

		assertEquals(FIXED, written.toString("UTF-8"))
		assertEquals(listOf(file), reimported)
		assertFalse("the fixed file leaves no editor behind", Dialogs.isShowing<WordListFixPopup>())
	}

	@Test
	fun `the button at the right edge is the one that saves`() {
		showFix()

		field.performTextInput(FIX)
		confirmButton().performClick()

		assertEquals("the spot read as the confirm must be the save", FIXED, written.toString("UTF-8"))
		assertEquals(listOf(file), reimported)
	}

	@Test
	fun `a document refusing the text keeps the fix on screen`() {
		showFix(target = Uri.parse("content://schlmgr.test/read-only.txt"))

		field.performTextInput(FIX)
		rule.onNodeWithText(text(R.string.popup_save_retry)).performClick()

		rule.onNodeWithText(text(R.string.popup_save_failed)).assertIsDisplayed()
		field.assertTextEquals(FIXED)
		assertTrue("the editor waits for a writable document", Dialogs.isShowing<WordListFixPopup>())
		assertTrue("nothing is imported from an unwritten file", reimported.isEmpty())
	}

	@Test
	fun `a tap aside keeps an unsaved fix`() {
		showFix()

		assertTrue("an untouched file may close", popup.canDismissByOutsideTouch())

		field.performTextInput(FIX)

		assertFalse("a typed fix survives a tap aside", popup.canDismissByOutsideTouch())
	}

	private companion object {
		const val NAME = "list.txt"
		const val SOURCE = "Haus\nWasser;water\n"
		const val FIXED = "Haus;home\nWasser;water\n"
		const val FIX = ";home"
		const val CARET = 4

		/** a file whose failing line sits below the fold of the editor */
		val DEEP = (1..40).joinToString("\n") { "Wort$it;text$it" } + "\nFalsch\n"

		/** the caret of the editor, both edges of its selection */
		fun caretOn(offset: Int) = SemanticsMatcher.expectValue(
			SemanticsProperties.TextSelectionRange, TextRange(offset, offset),
		)

		/** the field holds the focus, which brings the keyboard over the file */
		fun focused(held: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Focused, held)

		/**
		 * A file scrolled past [minPx] of its top.
		 * - the report above never scrolls on open, so a moved area is the file's own.
		 */
		fun fileScrolledPast(minPx: Float) = SemanticsMatcher("the file is scrolled past $minPx") { node ->
			val axis = SemanticsProperties.VerticalScrollAxisRange
			node.config.contains(axis) && node.config[axis].value() > minPx
		}
	}
}
