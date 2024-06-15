package cz.cvut.fit.litosjos.features.subject.presentation

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
import cz.cvut.fit.litosjos.features.subject.domain.Subject

@Composable
fun SubjectListItem(
	item: Subject,
	settings: Settings,
	onOpen: () -> Unit,
	onDelete: () -> Unit,
) {
	var editing by rememberSaveable { mutableStateOf(false) }
	if (editing) SubjectDialog(item = item) { editing = false }

	SwipeContainer(onDelete = onDelete, onUpdate = { editing = true }) {
		ListItem(item, R.drawable.ic_subject, settings, Modifier.clickable(onClick = onOpen))
	}
}