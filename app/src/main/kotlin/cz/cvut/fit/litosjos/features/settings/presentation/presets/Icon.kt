package cz.cvut.fit.litosjos.features.settings.presentation.presets

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cz.cvut.fit.litosjos.features.settings.theme.Sizes

@Composable
fun Icon(
	iconId: Int,
	modifier: Modifier = Modifier,
	tint: Color = LocalContentColor.current,
	contentDescriptionId: Int? = null,
) {
	androidx.compose.material3.Icon(
		painter = painterResource(id = iconId),
		contentDescription = contentDescriptionId?.let { stringResource(it) },
		modifier.size(Sizes.icon),
		tint = tint
	)
}

@Composable
fun RowFillIcon(
	iconId: Int,
	modifier: Modifier = Modifier,
	tint: Color = LocalContentColor.current,
	contentDescriptionId: Int? = null,
) {
	androidx.compose.material3.Icon(
		painter = painterResource(id = iconId),
		contentDescription = contentDescriptionId?.let { stringResource(it) },
		modifier
			.fillMaxHeight()
			.padding(Sizes.padding)
			.size(Sizes.icon),
		tint = tint
	)
}

@Composable
fun Icon(
	imageVector: ImageVector,
	modifier: Modifier = Modifier,
	tint: Color = LocalContentColor.current,
	contentDescriptionId: Int? = null,
) {
	androidx.compose.material3.Icon(
		imageVector = imageVector,
		contentDescription = contentDescriptionId?.let { stringResource(it) },
		modifier.size(Sizes.icon),
		tint = tint
	)
}

@Composable
fun RowFillIcon(
	imageVector: ImageVector,
	modifier: Modifier = Modifier,
	tint: Color = LocalContentColor.current,
	contentDescriptionId: Int? = null,
) {
	androidx.compose.material3.Icon(
		imageVector = imageVector,
		contentDescription = contentDescriptionId?.let { stringResource(it) },
		modifier
			.fillMaxHeight()
			.padding(Sizes.padding)
			.size(Sizes.icon),
		tint = tint
	)
}