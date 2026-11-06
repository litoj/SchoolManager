package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreatorPopupRequiredFieldsTest {

	@get:Rule
	val rule = createComposeRule()

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private fun wordPopup() =
		CreatorPopup(CreatorPopup.Kind.NEW_WORD, null, CreatorContent.Word(emptyList()))
			.apply { setOkListener { false } }

	@Test
	fun `ok discards empty rows and outlines the names still required`() {
		val popup = wordPopup()
		popup.translations.add(TranslationRow("", "", null))
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		val pixels = rule.onRoot().captureToImage().toPixelMap()
		assertTrue("the item name needs a red outline",
			hasRedOutline(pixels, rule.onNodeWithText("Name", useUnmergedTree = true)))
		val translation = rule.onNodeWithText("Translation", useUnmergedTree = true)
		assertTrue("the remaining translation needs a red outline", hasRedOutline(pixels, translation))
		val descriptions = rule.onAllNodesWithText("Description", useUnmergedTree = true)
		descriptions.assertCountEquals(2)
		for (i in 0 until 2)
			assertFalse("description $i stays optional", hasRedOutline(pixels, descriptions[i]))
	}

	@Test
	fun `ok restores a required translation row after its last row was deleted`() {
		val popup = wordPopup()
		popup.name = "Haus"
		popup.translations.clear()
		rule.setContent { popup.Content() }

		rule.onNodeWithText("OK").performClick()

		rule.runOnIdle { assertEquals(1, popup.translations.size) }
		val pixels = rule.onRoot().captureToImage().toPixelMap()
		assertFalse("the filled item name stays neutral",
			hasRedOutline(pixels, rule.onNodeWithText("Haus", useUnmergedTree = true)))
		assertTrue("the restored translation needs a red outline",
			hasRedOutline(pixels, rule.onNodeWithText("Translation", useUnmergedTree = true)))
	}

	/** Reads the border immediately outside the field text, whose left inset is about 6 dp. */
	private fun hasRedOutline(pixels: PixelMap, field: SemanticsNodeInteraction): Boolean {
		val bounds = field.getUnclippedBoundsInRoot()
		val y = with(rule.density) { ((bounds.top + bounds.bottom) / 2).toPx() }.roundToInt()
		val firstX = with(rule.density) { (bounds.left - 10.dp).toPx() }.roundToInt().coerceAtLeast(0)
		val lastX = with(rule.density) { (bounds.left - 2.dp).toPx() }.roundToInt()
			.coerceAtMost(pixels.width - 1)
		return (firstX..lastX).any { x ->
			val color = pixels[x, y]
			color.red > 0.5f && color.red > color.green * 2 && color.red > color.blue * 2
		}
	}
}
