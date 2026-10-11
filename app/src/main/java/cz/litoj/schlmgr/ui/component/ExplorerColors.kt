package cz.litoj.schlmgr.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import cz.litoj.schlmgr.R

/**
 * The explorer's color scheme, pulled from the app theme (`colors.xml`) so
 * the Compose screens follow the XML palette. [successRatioBackground] is
 * the one computed color.
 */
object ExplorerColors {
	/** Header green — the breadcrumb strip and accents. */
	val header: Color @Composable get() = colorResource(R.color.colorPrimaryHeader)

	/** Body text uses the foreground color so it stays readable on the background surface. */
	val body: Color @Composable get() = colorResource(R.color.colorPrimaryFg)

	/** Label color on the green header — breadcrumbs and enabled action labels. */
	val onHeader: Color @Composable get() = colorResource(R.color.colorOnHeader)

	/** Position numbers carry their own color so they stand out from body text in the cell. */
	val positionText: Color @Composable get() = colorResource(R.color.colorPositionText)

	/** Descriptions are secondary info, so they use a distinct green rather than body color. */
	val description: Color @Composable get() = colorResource(R.color.colorDescText)

	/** The action strip has its own grey wash so it never flips with the green header bar. */
	val actionBar: Color @Composable get() = colorResource(R.color.colorActionBar)

	/** Enabled action label of those strips — their backdrop is the grey wash above,
	 *  not the green header, so it does not flip with [onHeader]. */
	val actionText: Color @Composable get() = colorResource(R.color.colorActionText)

	/** Disabled action labels need lower contrast so enabled actions remain the focus. */
	val actionTextDisabled: Color @Composable get() = colorResource(R.color.colorActionTextDisabled)

	/** The selected row's highlight is subtle green so selection is visible without hiding the ratio tint. */
	val selectedRow: Color @Composable get() = colorResource(R.color.colorSelectedRow)
}
