package cz.cvut.fit.litosjos.features.word.presentation

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.item.ListItem
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.settings.presentation.presets.SwipeContainer
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WordListItem(
	state: WordListItemState,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	onDelete: (Item) -> Unit,
	onClick: () -> Unit,
) {
	SwipeContainer(item = state.word.base, onDelete = onDelete, onUpdate = {
		dialogUpdater { WordDialog(item = state.word, onDismiss = { dialogUpdater(null) }) }
	}) {
		ListItem(
			item = if (state.showTranslated) it.copy(name = state.word.translations) else it,
			icon = R.drawable.ic_word,
			settings = state.settings,
			dialogUpdater = dialogUpdater,
			Modifier.clickable(onClick = onClick)
		)
	}
}

@Composable
fun WordListItem(
	item: Item,
	sharedViewModel: ListItemSharedViewModel,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	onDelete: (Item) -> Unit,
) {
	val viewModel: WordListItemViewModel =
		koinViewModel(parameters = { parametersOf(item, sharedViewModel) }, key = item.id.toString())

	val state by viewModel.state.collectAsStateWithLifecycle()

	WordListItem(
		state = state,
		dialogUpdater = dialogUpdater,
		onDelete = onDelete,
		onClick = viewModel::toggleTranslated
	)
}
