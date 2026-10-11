package cz.litoj.schlmgr.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

/**
 * The drawer's destinations — the flat sibling screens of the navigation graph.
 * - the enum's name is the navigation route.
 * - the pushed picker screen is not a destination ([PICKER_ROUTE] is its route).
 */
enum class Destination(val label: Int, val icon: Int) {
	SUBJECTS(R.string.menu_objects, R.drawable.ic_subject),
	TEST(R.string.select_test, R.drawable.ic_test_black),
	SETTINGS(R.string.menu_settings, R.drawable.ic_menu_settings),
	ABOUT(R.string.menu_about, R.drawable.ic_menu_app),
}

/** The pushed source-picker destination, opened from the test setup screen. */
const val PICKER_ROUTE = "PICKER"

/**
 * The navigation drawer — 250dp panel on the background surface, gradient
 * header, destinations with icons.
 * - taps navigate flat (no saved stacks).
 */
@Composable
fun AppDrawer(
	selected: Destination,
	onNavigate: (Destination) -> Unit,
) {
	Column(
		Modifier
			.width(DrawerWidth)
			.fillMaxHeight()
			// the drawer's surface covers the system bars too — the background
			// comes before the paddings, so it fills the whole width and height
			.background(colorResource(R.color.colorPrimaryBg))
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
		// the header — the gradient band with the app icon and its name
		Box(
			Modifier
				.fillMaxWidth()
				.height(55.dp)
				.background(
					// the gradient runs at angle 225° — soft at the top-right
					// corner, the header green at the bottom-left
					Brush.linearGradient(
						listOf(
							colorResource(R.color.colorHeaderSoft),
							colorResource(R.color.colorPrimaryHeader),
						),
						start = Offset(Float.POSITIVE_INFINITY, 0f),
						end = Offset(0f, Float.POSITIVE_INFINITY),
					)
				),
			contentAlignment = Alignment.Center,
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Image(
					painter = painterResource(R.drawable.ic_app),
					contentDescription = null,
					modifier = Modifier.size(40.dp),
				)
				Spacer(Modifier.width(5.dp))
				Text(
					stringResource(R.string.menu_label),
					style = TextStyle(fontSize = 30.sp, color = colorResource(R.color.colorOnHeader)),
				)
			}
		}
		Spacer(Modifier.height(8.dp))
		for (destination in Destination.entries) {
			DestinationRow(
				destination,
				selected = destination == selected,
				onClick = { onNavigate(destination) },
			)
		}
	}
}

@Composable
private fun DestinationRow(
	destination: Destination,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.height(48.dp)
			.then(
				if (selected) Modifier.background(colorResource(R.color.colorHighlight))
				else Modifier
			)
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Image(
			painter = painterResource(destination.icon),
			contentDescription = null,
			modifier = Modifier.size(24.dp),
			// the theme's foreground overrides each drawable's own tint
			// (ic_subject and ic_menu_settings carry green ones)
			colorFilter = ColorFilter.tint(colorResource(R.color.colorPrimaryFg)),
		)
		Spacer(Modifier.width(32.dp))
		Text(
			stringResource(destination.label),
			// the NavigationView's item text size
			style = TextStyle(fontSize = 14.sp, color = colorResource(R.color.colorPrimaryFg)),
		)
	}
}
