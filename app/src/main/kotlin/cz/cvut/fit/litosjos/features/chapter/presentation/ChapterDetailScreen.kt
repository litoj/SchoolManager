package cz.cvut.fit.litosjos.features.chapter.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.ItemType
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.picture.presentation.PictureDialog
import cz.cvut.fit.litosjos.features.picture.presentation.PictureListItem
import cz.cvut.fit.litosjos.features.settings.SettingsActivity
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Icon
import cz.cvut.fit.litosjos.features.settings.theme.Sizes
import cz.cvut.fit.litosjos.features.word.domain.Word
import cz.cvut.fit.litosjos.features.word.presentation.WordDialog
import cz.cvut.fit.litosjos.features.word.presentation.WordListItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun NewItemDropdown(expanded: Boolean, dialogUpdater: ((@Composable () -> Unit)?) -> Unit) {
	DropdownMenu(expanded = expanded, onDismissRequest = { dialogUpdater(null) }) {
		Text(
			text = stringResource(R.string.new_item),
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier.padding(Sizes.padding)
		)
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_chapter) },
			text = { Text(text = stringResource(R.string.chapter)) },
			onClick = {
				dialogUpdater { ChapterDialog(Item(type = ItemType.CHAPTER)) { dialogUpdater(null) } }
			})
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_word) },
			text = { Text(text = stringResource(R.string.word)) },
			onClick = {
				dialogUpdater { WordDialog(Word(Item(type = ItemType.WORD), "")) { dialogUpdater(null) } }
			})
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_picture) },
			text = { Text(text = stringResource(R.string.picture)) },
			onClick = {
				dialogUpdater {
					PictureDialog(Picture(Item(type = ItemType.PICTURE), "")) { dialogUpdater(null) }
				}
			})
	}
}

enum class DropdownAction {
	NONE, OPEN, NEW_ITEM, SETTINGS
}

@Composable
fun ActionsDropdown(expanded: Boolean, onChoice: (DropdownAction) -> Unit) {
	DropdownMenu(expanded = expanded, onDismissRequest = { onChoice(DropdownAction.NONE) }) {
		DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Add) },
			text = { Text(text = stringResource(R.string.new_item)) },
			onClick = { onChoice(DropdownAction.NEW_ITEM) })
		DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Settings) },
			text = { Text(text = stringResource(R.string.settings)) },
			onClick = { onChoice(DropdownAction.SETTINGS) })
	}
}

@Composable
fun ChapterScreen(
	navController: NavController,
	breadCrumbs: @Composable () -> Unit = { /* TODO */ },
	viewModel: ChapterScreenViewModel = koinViewModel()
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val sharedViewModel: ListItemSharedViewModel = koinViewModel()
	// TODO: should this be separate to avoid entire chapter list recomposition?
	val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()
	BackHandler {
		if (sharedState.dialog != null) sharedViewModel.update(sharedState.copy(dialog = null))
		else navController.popBackStack()
	}

	var dropdownChoice by remember { mutableStateOf(DropdownAction.NONE) }
	if (dropdownChoice == DropdownAction.SETTINGS) {
		dropdownChoice = DropdownAction.NONE
		startActivity(
			LocalContext.current,
			Intent(LocalContext.current, SettingsActivity::class.java),
			null
		)
	}

	Scaffold(topBar = {
		Column {
			TopAppBar(title = {
				Text(text = state.source.name, style = MaterialTheme.typography.headlineMedium)
			}, actions = {
				IconButton(onClick = { dropdownChoice = DropdownAction.OPEN }) {
					Icon(Icons.Default.MoreVert, contentDescriptionId = R.string.new_item)
				}

				ActionsDropdown(expanded = dropdownChoice == DropdownAction.OPEN) { dropdownChoice = it }
				NewItemDropdown(expanded = dropdownChoice == DropdownAction.NEW_ITEM) {
					dropdownChoice = DropdownAction.NONE
					sharedViewModel.update(sharedState.copy(dialog = it))
				}
			})
			breadCrumbs()
		}
	}) {
		sharedState.dialog?.invoke()

		val dialogUpdater =
			{ it: (@Composable () -> Unit)? -> sharedViewModel.update(sharedState.copy(dialog = it)) }
		val onOpen: (Item) -> Unit = { navController.navigate(Screens.of(it)) }
		val onDelete: (Item) -> Unit = { viewModel.deleteChild(it) }

		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(it)
		) {
			val list = state.items
			items(count = list.size, key = { idx -> list[idx].id }) { idx ->
				when (list[idx].type) {
					ItemType.CHAPTER -> ChapterListItem(
						item = list[idx],
						settings = sharedState.settings,
						dialogUpdater = dialogUpdater,
						onOpen = onOpen,
						onDelete = onDelete
					)

					ItemType.PICTURE -> PictureListItem(
						item = list[idx],
						sharedViewModel = sharedViewModel,
						dialogUpdater = dialogUpdater,
						onDelete = onDelete
					)

					ItemType.WORD -> WordListItem(
						item = list[idx],
						sharedViewModel = sharedViewModel,
						dialogUpdater = dialogUpdater,
						onDelete = onDelete
					)

					else -> {}
				}
			}
		}
	}
}

