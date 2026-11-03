package cz.litoj.schlmgr.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

private val GroupBackground @Composable get() = colorResource(R.color.colorGroupBackground)
private val SubBackground @Composable get() = colorResource(R.color.colorSubBackground)

/**
 * The top-level section band: the green header with an icon, shared by the help tree
 * and the settings screen.
 * - [tint] colours monochrome band icons, since green ones vanish on the green band.
 */
@Composable
fun BigHeader(text: String, @DrawableRes icon: Int, tint: Color = Color.Unspecified) {
	Row(
		Modifier
			.fillMaxWidth()
			.background(colorResource(R.color.colorPrimaryHeader))
			.padding(horizontal = 15.dp, vertical = 3.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text,
			Modifier.weight(1f),
			// the on-header colour keeps the green header chrome readable.
			// it also works when the header turns bright and its text dark.
			style = TextStyle(fontSize = 25.sp, color = colorResource(R.color.colorOnHeader)),
		)
		Icon(painterResource(icon), null, tint = tint)
	}
}

/** The visuals of one section level, shared by the help tree and the settings subsections. */
data class SectionStyle(val background: Color, val fontSize: TextUnit, val vertical: Dp, val indent: Dp)

/**
 * The visuals of a section header for its level.
 * - the deepest level has no band left, so its indentation carries the depth.
 */
@Composable
fun sectionStyle(level: Int): SectionStyle = when (level) {
	0 -> SectionStyle(GroupBackground, 19.sp, 7.dp, 0.dp)
	1 -> SectionStyle(SubBackground, 17.sp, 2.dp, 0.dp)
	else -> SectionStyle(Color.Transparent, 15.sp, 2.dp, 15.dp * (level - 1))
}

/** A static section band at one level, see [sectionStyle]. */
@Composable
fun SectionHeader(text: String, level: Int = 1) {
	val style = sectionStyle(level)
	Text(
		text,
		Modifier
			.fillMaxWidth()
			.background(style.background)
			.padding(start = 15.dp + style.indent, end = 15.dp, top = style.vertical, bottom = style.vertical),
		style = TextStyle(fontSize = style.fontSize, color = DefaultTextColor),
	)
}
