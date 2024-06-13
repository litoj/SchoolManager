package cz.cvut.fit.litosjos.core.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Dialog
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Icon
import cz.cvut.fit.litosjos.features.settings.presentation.presets.RowFillIcon
import cz.cvut.fit.litosjos.features.settings.theme.Sizes

@Composable
fun DescriptionDialog(name: String, description: String, onDismissRequest: () -> Unit) {
	Dialog(onDismissRequest, {
		Text(
			text = name,
			style = MaterialTheme.typography.titleMedium,
			modifier = Modifier
				.padding(end = Sizes.padding)
				.weight(1f)
		)
		Icon(R.drawable.ic_info, tint = MaterialTheme.colorScheme.surfaceVariant)
	}) {
		Text(text = description)
	}
}

@Composable
fun DescriptionInfoWrapper(
	name: String,
	description: String,
	// TODO: I expect passing params faster then settings repo flow subscription per each item, test it
	maxLinesPreview: Int,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	modifier: Modifier,
	content: @Composable (Modifier) -> Unit,
) {
	// TODO: should we remember this?
	val descriptionLineCount = if (description.isEmpty()) 0 else description.count { it == '\n' } + 1

	if (descriptionLineCount > maxLinesPreview) {
		content(modifier)
		RowFillIcon(R.drawable.ic_info,
			Modifier
				.clip(RoundedCornerShape(Sizes.roundness))
				.clickable {
					dialogUpdater {
						DescriptionDialog(name = name, description = description) {
							dialogUpdater(null)
						}
					}
				})
	} else Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
		content(Modifier)
		if (descriptionLineCount > 0) Text(
			text = description,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = Sizes.padding)
		)
	}
}