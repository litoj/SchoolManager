package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R

/**
 * The one separator of the app, a full-width hairline between stacked rows and strips.
 * - always in the divider gray of the app, with no per-surface variants.
 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
	Spacer(modifier.fillMaxWidth().height(0.6.dp).background(colorResource(R.color.colorDivider)))
}
