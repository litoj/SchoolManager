package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.ColorizedItemIcon
import cz.cvut.fit.litosjos.core.presentation.DescriptionInfoWrapper
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.presentation.presets.SwipeContainer
import cz.cvut.fit.litosjos.features.settings.theme.Sizes

@Composable
fun PictureListItem(
	item: Picture,
	settings: Settings,
	onDelete: () -> Unit,
	onToggleAll: (on: Boolean) -> Unit,
) {
	var toggled by rememberSaveable(key = item.id.toString()) { mutableStateOf(settings.showTranslated) }
	var settingsToggle by rememberSaveable { mutableStateOf(settings.showTranslated) }
	if (settingsToggle != settings.showTranslated) {
		toggled = settings.showTranslated
		settingsToggle = settings.showTranslated
	}

	var editing by rememberSaveable { mutableStateOf(false) }
	if (editing) PictureDialog(item = item) { editing = false }

	SwipeContainer(onDelete = onDelete, onUpdate = { editing = true }) {
		Column {
			Row(modifier = Modifier
				.clickable {
					if (settings.toggleAll) onToggleAll(!toggled) else toggled = !toggled
				}
				.background(MaterialTheme.colorScheme.background)
				.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically) {
				ColorizedItemIcon(R.drawable.ic_picture, item.passedTests, item.failedTests, settings)
				DescriptionInfoWrapper(
					name = item.name,
					description = item.description,
					maxLinesPreview = settings.descriptionLineCount,
					modifier = Modifier
						.weight(1f)
						.padding(vertical = Sizes.padding, horizontal = 1.dp),
				) {
					if (toggled) AsyncImage(
						model = item.uri,
						contentDescription = item.description,
						modifier = it
							.fillMaxSize()
							.heightIn(0.dp, 100.dp)
					) else Text(text = item.name, modifier = it)
				}
			}
			HorizontalDivider()
		}
	}
}