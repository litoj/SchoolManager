package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.component.Item
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the measure is the only source.
 * - a fixed minimum would hold the number far from its type icon.
 * - it would also take half the row on a short list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PositionColumnWidthTest {

	@get:Rule
	val rule = createComposeRule()

	@Composable
	private fun RowOf(position: Int, maxPosition: Int, variant: ExplorerRowVariant) {
		Item(
			item = ItemUiModel(
				key = "k$position",
				position = position,
				name = "row $position",
				description = "",
				kind = RowKind.SUBJECT,
				ratio = 0,
				isReference = false,
				selected = false,
			),
			onClick = {},
			onLongClick = {},
			descVisible = false,
			variant = variant,
			positionWidth = positionColumnWidth(maxPosition, variant),
		)
	}

	private fun cellWidth(variant: ExplorerRowVariant): Dp {
		val bounds = rule.onNodeWithText("8.", useUnmergedTree = true).getUnclippedBoundsInRoot()
		return bounds.right - bounds.left
	}

	@Test
	fun `a short browse list does not pad the column to a fixed 58dp`() {
		rule.setContent {
			Column { RowOf(position = 8, maxPosition = 8, variant = ExplorerRowVariant.Browse) }
		}
		val width = cellWidth(ExplorerRowVariant.Browse)
		assertTrue("the column must follow the number, not a fixed minimum: $width", width < 58.dp)
	}

	@Test
	fun `a short search list does not pad the column to a fixed 65dp`() {
		rule.setContent {
			Column { RowOf(position = 8, maxPosition = 8, variant = ExplorerRowVariant.Search) }
		}
		val width = cellWidth(ExplorerRowVariant.Search)
		assertTrue("the column must follow the number, not a fixed minimum: $width", width < 65.dp)
	}

	@Test
	fun `the widest number of the list decides the width of every cell`() {
		rule.setContent {
			Column {
				RowOf(position = 8, maxPosition = 8, variant = ExplorerRowVariant.Browse)
				RowOf(position = 8, maxPosition = 1000, variant = ExplorerRowVariant.Browse)
			}
		}
		val widths = rule.onAllNodesWithText("8.", useUnmergedTree = true)
			.fetchSemanticsNodes()
			.map { (it.boundsInRoot.right - it.boundsInRoot.left) }
		assertTrue("both cells must be measured: $widths", widths.size == 2)
		assertTrue("a four-digit list needs a wider cell: $widths", widths[1] > widths[0])
	}
}
