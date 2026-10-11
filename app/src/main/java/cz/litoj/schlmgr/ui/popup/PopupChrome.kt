package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

/**
 * The small dialogs' shared look — a theme-background card, only as
 * tall as its content. A centered [Box] hugs it, so no dead space
 * surrounds the bar.
 */
@Composable
internal fun PopupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(
			Modifier
				.padding(30.dp)
				.fillMaxWidth()
				.background(colorResource(R.color.colorPrimaryBg))
		) {
			content()
		}
	}
}

@Composable
internal fun VerticalButtonSeparator() {
	Spacer(
		Modifier
			.width(0.5.dp)
			.fillMaxHeight()
			.padding(vertical = 10.dp)
			.background(colorResource(R.color.colorStripDivider))
	)
}

@Composable
internal fun PopupButton(label: String, modifier: Modifier, onClick: () -> Unit) {
	Box(
		modifier
			.fillMaxHeight()
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Text(
			label,
			Modifier.padding(horizontal = 5.dp),
			style = TextStyle(fontSize = 18.sp, color = TextColor),
		)
	}
}

internal val TextColor @Composable get() = colorResource(R.color.colorPrimaryFg)
