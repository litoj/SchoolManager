package cz.cvut.fit.litosjos.features.word.presentation

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ListItem
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.presentation.presets.SwipeContainer
import cz.cvut.fit.litosjos.features.word.domain.Word

@Composable
fun WordListItem(
	item: Word,
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
	if (editing) WordDialog(item = item) { editing = false }

	SwipeContainer(onDelete = onDelete, onUpdate = { editing = true }) {
		ListItem(item = if (toggled) item.copy(name = item.translations) else item,
			icon = R.drawable.ic_word,
			settings = settings,
			Modifier.clickable { if (settings.toggleAll) onToggleAll(!toggled) else toggled = !toggled })
	}
}