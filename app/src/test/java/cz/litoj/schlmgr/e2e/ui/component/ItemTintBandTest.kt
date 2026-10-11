package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The success-ratio band spans the row's full height — the height the text
 * column sets, not the cell's own content height. The band is only drawn
 * (no layout filler), so the pin reads the row's rendered pixels: the tint
 * at the very bottom must match the tint at the top, and must not bleed
 * over the whole row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// captureToImage reads real pixels — Robolectric must render with the native
// graphics pipeline for the row's bitmap to exist
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemTintBandTest {

	@get:Rule
	val rule = createComposeRule()

	@Test
	fun `the success-ratio band spans the row's full height`() {
		rule.setContent {
			Item(
				item = ItemUiModel(
					key = "k",
					position = 1,
					// a four-line parsed name — the text column towers over the cell
					name = "(an der ersten Stelle)\n(auf dem ersten Platz)" +
						"\n(und noch eine Zeile)\n(die vierte Zeile)",
					description = "",
					kind = RowKind.WORD,
					ratio = 50,
					isReference = false,
					selected = false,
				),
				onClick = {},
				onLongClick = {},
				descVisible = false,
				positionWidth = positionColumnWidth(1, ExplorerRowVariant.Browse),
			)
		}

		val image = rule.onNode(hasClickAction()).captureToImage()
		val pixels = image.toPixelMap()
		val w = image.width
		val h = image.height
		assertTrue("a four-line row must tower over the cell: ${h}px", h > 90)
		val bandTop = pixels[2, 2]
		val bandBottom = pixels[2, h - 3]
		val rowTop = pixels[w - 5, 2]
		assertNotEquals("the band must be painted beside the untinted row", bandTop, rowTop)
		assertEquals("the band must reach the row's bottom", bandTop, bandBottom)
	}
}
