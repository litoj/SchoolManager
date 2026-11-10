package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

/** Only the direct count changes. Existing spacing and slot sizing stay as they are. */
internal class ActionLayout(
	val direct: Int,
	val showMore: Boolean,
)

internal const val ACTION_SLOT_RATIO = 1.2f

/**
 * [slotWidth] estimates how many actions fit. It changes no slot sizing or spacing.
 */
internal fun actionLayout(
	count: Int,
	width: Dp,
	reservedWidth: Dp = 0.dp,
	slotWidth: Dp,
): ActionLayout {
	require(count >= 0)
	require(slotWidth > 0.dp)
	val slots = (((width - reservedWidth).coerceAtLeast(0.dp) / slotWidth).toInt()).coerceAtLeast(0)
	val direct = when {
		count <= slots -> count
		count == 1 -> 1
		else -> (slots - 1).coerceAtLeast(0)
	}
	return ActionLayout(direct, showMore = count > direct && count - direct > 1)
}
