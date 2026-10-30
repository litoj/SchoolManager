package cz.litoj.schlmgr.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import cz.litoj.schlmgr.R

/**
 * The status band in the dark green of the drawer banner.
 * - the caller draws it last, so it covers whatever sits under the top bar.
 * - the top bar reserves only the height of the band.
 */
@Composable
internal fun StatusBarBand() {
	Box(
		Modifier
			.fillMaxWidth()
			.windowInsetsTopHeight(WindowInsets.statusBars)
			.background(colorResource(R.color.colorStatusBar))
	)
}
