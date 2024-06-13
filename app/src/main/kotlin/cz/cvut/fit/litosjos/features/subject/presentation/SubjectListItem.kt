package cz.cvut.fit.litosjos.features.subject.presentation

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.item.ListItem
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.presentation.presets.SwipeContainer

@Composable
fun SubjectListItem(
	item: Item,
	settings: Settings,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	onOpen: (Item) -> Unit,
	onDelete: (Item) -> Unit,
) {
	SwipeContainer(item = item, onDelete = onDelete, onUpdate = {
		dialogUpdater { SubjectDialog(item = item) { dialogUpdater(null) } }
	}) {
		ListItem(item,
			R.drawable.ic_subject,
			settings,
			dialogUpdater,
			Modifier.clickable { onOpen(item) })
	}
}