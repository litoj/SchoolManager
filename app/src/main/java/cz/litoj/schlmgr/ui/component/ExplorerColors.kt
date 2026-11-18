package cz.litoj.schlmgr.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import cz.litoj.schlmgr.R

/**
 * The color scheme of the explorer, pulled from the app theme colors.xml.
 * - the Compose screens follow the XML palette.
 * - [successRatioBackground] is the one computed color.
 */
object ExplorerColors {
	/** Header green: the breadcrumb strip and accents. */
	val header: Color @Composable get() = colorResource(R.color.colorPrimaryHeader)

	/** Body text uses the foreground color so it stays readable on the background surface. */
	val body: Color @Composable get() = colorResource(R.color.colorPrimaryFg)

	/** Label color on the green header: breadcrumbs and enabled action labels. */
	val onHeader: Color @Composable get() = colorResource(R.color.colorOnHeader)

	/** Position numbers carry their own color so they stand out from body text in the cell. */
	val positionText: Color @Composable get() = colorResource(R.color.colorPositionText)

	/** Descriptions are secondary info, so they use a distinct green rather than body color. */
	val description: Color @Composable get() = colorResource(R.color.colorDescText)

	/** The highlight of the selected row is subtle green, so selection shows without
	 *  hiding the ratio tint. */
	val selectedRow: Color @Composable get() = colorResource(R.color.colorSelectedRow)

	/** A jump landing flashes the landed row once in the header green. */
	val pulseRow: Color @Composable get() = header.copy(alpha = 0.40f)

	/** A light grey wash keeps small floating labels readable over both themes. */
	val subBackground: Color @Composable get() = colorResource(R.color.colorSubBackground)

	/** The same wash solid, for the New palette, so the list behind never mixes in. */
	val paletteBackground: Color @Composable get() = colorResource(R.color.colorPaletteBackground)

	/** The red of errors, tuned per theme to stay readable on the background surface. */
	val failure: Color @Composable get() = colorResource(R.color.colorFailure)
}
