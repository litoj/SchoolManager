package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Only what the popup itself adds: the rendered source block and its measured marker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportFailurePopupTest {

	@get:Rule
	val rule = createComposeRule()

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun parseError(source: String): WordListParseException =
		assertThrows(WordListParseException::class.java) {
			WordListReader.parse(source, "Broken.txt")
		}

	private fun show(source: String) {
		val popup = UserMessages.onWordListParseFail(parseError(source))
		rule.setContent { popup.Content() }
	}

	private fun layoutOf(node: SemanticsNode): TextLayoutResult {
		val layouts = mutableListOf<TextLayoutResult>()
		node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
		return layouts.single()
	}

	/** The marker and its source line share a scrollable row, so only measured bounds prove alignment. */
	private fun assertMarkerAligned(source: String, index: Int) {
		val markerNode = rule.onNodeWithText("↓", substring = true).fetchSemanticsNode()
		val sourceNode = rule.onNodeWithText(source).fetchSemanticsNode()
		val markerLayout = layoutOf(markerNode)
		val sourceLayout = layoutOf(sourceNode)
		val markerBounds = markerLayout.getBoundingBox(markerLayout.layoutInput.text.text.indexOf('↓'))
		val targetX = if (index == source.length) sourceLayout.getCursorRect(index).left
		else sourceLayout.getBoundingBox(index).let { (it.left + it.right) / 2 }

		assertEquals(
			sourceNode.positionInRoot.x + targetX,
			markerNode.positionInRoot.x + (markerBounds.left + markerBounds.right) / 2,
			0.5f,
		)
	}

	@Test
	fun `the popup renders the failing line with its marker`() {
		show("Haus->dům\n")

		rule.onNodeWithText("Haus->dům").assertExists()
		rule.onNodeWithText("↓", substring = true).assertExists()
	}

	@Test
	fun `the marker aligns to the measured center of a wide target character`() {
		val source = "漢字;translation;extra"
		show("$source\n")

		assertMarkerAligned(source, "漢字;translation".length)
	}

	@Test
	fun `the marker aligns to a measured end insertion point`() {
		val source = "Haus"
		show("$source\n")

		assertMarkerAligned(source, source.length)
	}

	@Test
	fun `the marker stays above a source line wider than the popup`() {
		val source = "very long word " + "tail ".repeat(30)
		show("$source\n")

		assertMarkerAligned(source, source.length)
	}
}
