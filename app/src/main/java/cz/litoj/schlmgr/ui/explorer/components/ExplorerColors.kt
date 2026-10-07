package cz.litoj.schlmgr.ui.explorer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import cz.litoj.schlmgr.R

/**
 * The explorer's color scheme, pulled from the app theme ([colors.xml]) so the
 * Compose screens follow the same palette the XML layouts defined — no
 * hard-coded values in composables. The only color the explorer computes
 * itself is the success-ratio background of the position cell
 * ([successRatioBackground]).
 */
object ExplorerColors {
	/** Header green — the breadcrumb strip and accents (`colorPrimaryHeader`). */
	val header: Color @Composable get() = colorResource(R.color.colorPrimaryHeader)

	/** Body text color matching the app's foreground (`colorPrimaryFg`). */
	val body: Color @Composable get() = colorResource(R.color.colorPrimaryFg)

	/** Background surface (`colorPrimaryBg`), used by the list and bottom bars. */
	val surface: Color @Composable get() = colorResource(R.color.colorPrimaryBg)

	/** Label color on the green header — breadcrumbs and enabled action labels. */
	val onHeader: Color @Composable get() = colorResource(R.color.colorOnHeader)

	/** The position number (`item_pos` text, `#000000` in the legacy layouts). */
	val positionText: Color @Composable get() = colorResource(R.color.colorPositionText)

	/** The green item-description line (legacy `#080`). */
	val desc: Color @Composable get() = colorResource(R.color.colorDescText)

	/** The 1px row separator (legacy `#4888`). */
	val rowDivider: Color @Composable get() = colorResource(R.color.colorRowDivider)

	/** The divider under the info strip (legacy `#888`). */
	val stripDivider: Color @Composable get() = colorResource(R.color.colorStripDivider)

	/** The bottom action-strip backdrop (legacy `#8000`). */
	val actionBar: Color @Composable get() = colorResource(R.color.colorActionBar)

	/** Enabled action label of those strips; their backdrop is the grey wash above,
	 *  not the green header, so it does not flip with [onHeader]. */
	val actionText: Color @Composable get() = colorResource(R.color.colorActionText)

	/** Disabled action label (legacy `#6FFF`). */
	val actionTextDisabled: Color @Composable get() = colorResource(R.color.colorActionTextDisabled)
}
