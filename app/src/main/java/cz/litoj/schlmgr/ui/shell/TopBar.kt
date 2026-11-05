package cz.litoj.schlmgr.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.IconSize
import cz.litoj.schlmgr.ui.component.reportUnavailable

/**
 * The chrome of the top bar: the status strip above the 55dp row on the header colour.
 * - the screen supplies the pieces of the row, all tinted with the on-header colour.
 * - that covers [TopBarNav], the title, the icon buttons and the more button.
 *
 * @param content the pieces of the row, with a weighted middle child
 */
@Composable
fun TopBar(content: @Composable RowScope.() -> Unit) {
	Column(
		Modifier
			.fillMaxWidth()
			.statusBarsPadding()
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.height(55.dp)
				.background(colorResource(R.color.colorPrimaryHeader)),
			verticalAlignment = Alignment.CenterVertically,
			content = content,
		)
	}
}

/**
 * The navigation slot: [up] shows the up arrow instead of the drawer hamburger.
 * - a false [enabled] keeps the arrow and explains its [blockedReason] on a tap.
 */
@Composable
fun RowScope.TopBarNav(
	up: Boolean = false,
	onNavigate: () -> Unit,
	enabled: Boolean = true,
	blockedReason: Int = 0,
	contentDescription: String,
) {
	val tint = colorResource(R.color.colorOnHeader).let { if (enabled) it else it.copy(alpha = 0.5f) }
	Box(
		Modifier
			.testTag("top_bar_nav")
			.width(60.dp)
			.fillMaxHeight()
			.clickable { if (enabled) onNavigate() else reportUnavailable(blockedReason) },
		contentAlignment = Alignment.Center,
	) {
		Icon(
			if (up) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
			contentDescription = contentDescription,
			tint = tint,
		)
	}
}

@Composable
fun RowScope.TopBarTitle(title: String) {
	Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
		Text(
			title,
			style = TextStyle(fontSize = 20.sp, color = colorResource(R.color.colorOnHeader)),
		)
	}
}

/**
 * A 50dp bar button, where a false [visible] leaves the slot out.
 * - the search button hides while the search field takes the place of the title.
 * - a false [enabled] mutes the icon and explains its [blockedReason] on a tap.
 */
@Composable
fun RowScope.TopBarIconButton(
	@DrawableRes icon: Int,
	contentDescription: String,
	visible: Boolean = true,
	enabled: Boolean = true,
	blockedReason: Int = 0,
	onClick: () -> Unit,
) {
	if (!visible) return
	val tint = colorResource(R.color.colorOnHeader).let { if (enabled) it else it.copy(alpha = 0.5f) }
	Box(
		Modifier
			.width(50.dp)
			.fillMaxHeight()
			.clickable { if (enabled) onClick() else reportUnavailable(blockedReason) },
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = painterResource(icon),
			contentDescription = contentDescription,
			modifier = Modifier.size(IconSize),
			colorFilter = ColorFilter.tint(tint),
		)
	}
}

/**
 * The more-options button, where a press opens the entries as a dropdown under it.
 * The button owns its dropdown, so no screen carries the open state.
 */
@Composable
fun RowScope.TopBarMoreButton(
	entries: List<MenuEntry>,
	contentDescription: String,
	onPick: (Int) -> Unit,
) {
	var open by remember { mutableStateOf(false) }
	Box(
		Modifier
			.width(50.dp)
			.fillMaxHeight()
			.clickable { open = true },
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = painterResource(R.drawable.ic_more),
			contentDescription = contentDescription,
			modifier = Modifier.size(IconSize),
			colorFilter = ColorFilter.tint(colorResource(R.color.colorOnHeader)),
		)
		if (open) MoreMenu(entries, onPick) { open = false }
	}
}

/**
 * The elevation shadow of the bar over the top of the content.
 * - it must end at [ShadowHeight], since the default ramp end collapses these pixels.
 */
fun Modifier.headerShadow(): Modifier = drawWithContent {
	drawContent()
	val height = ShadowHeight.toPx()
	drawRect(
		brush = Brush.verticalGradient(
			colors = listOf(Color(0x40000000), Color(0x17000000), Color.Transparent),
			startY = 0f,
			endY = height,
		),
		size = Size(size.width, height),
	)
}

private val ShadowHeight = 6.dp
