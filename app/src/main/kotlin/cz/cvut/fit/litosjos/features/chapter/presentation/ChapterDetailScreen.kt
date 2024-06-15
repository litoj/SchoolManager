package cz.cvut.fit.litosjos.features.chapter.presentation

import android.content.Intent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.features.chapter.domain.Chapter
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
fun NewItemDropdown(expanded: Boolean, onChoice: (typeName: String?) -> Unit) {
	DropdownMenu(expanded = expanded, onDismissRequest = { onChoice(null) }) {
		Text(
			text = stringResource(R.string.new_item),
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier.padding(Sizes.padding)
		)
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_chapter) },
			text = { Text(text = stringResource(R.string.chapter)) },
			onClick = { onChoice("chapter") })
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_word) },
			text = { Text(text = stringResource(R.string.word)) },
			onClick = { onChoice("word") })
		DropdownMenuItem(leadingIcon = { Icon(R.drawable.ic_picture) },
			text = { Text(text = stringResource(R.string.picture)) },
			onClick = { onChoice("picture") })
	}
}

enum class DropdownAction {
	NONE, OPEN, NEW_ITEM
}

@Composable
fun ActionsDropdown(expanded: Boolean, onChoice: (DropdownAction) -> Unit) {
	val context = LocalContext.current
	DropdownMenu(expanded = expanded, onDismissRequest = { onChoice(DropdownAction.NONE) }) {
		DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Add) },
			text = { Text(text = stringResource(R.string.new_item)) },
			onClick = { onChoice(DropdownAction.NEW_ITEM) })
		DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Settings) },
			text = { Text(text = stringResource(R.string.settings)) },
			onClick = {
				onChoice(DropdownAction.NONE)
				startActivity(context, Intent(context, SettingsActivity::class.java), null)
			})
	}
}

@Composable
fun ChapterScreen(
	navigate: (route: String) -> Unit,
	breadCrumbs: @Composable () -> Unit = { /* TODO */ },
	viewModel: ChapterDetailScreenViewModel = koinViewModel()
) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	var creating by rememberSaveable { mutableStateOf<String?>(null) }
	when (creating) {
		"chapter" -> ChapterDialog(item = Chapter(Item(parentId = state.source.id))) { creating = null }
		"picture" -> PictureDialog(item = Picture(Item(parentId = state.source.id))) { creating = null }
		"word" -> WordDialog(item = Word(Item(parentId = state.source.id))) { creating = null }
		else -> {}
	}

	var dropdownChoice by remember { mutableStateOf(DropdownAction.NONE) }

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
					creating = it
				}
			})
			breadCrumbs()
		}
	}) {
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(it)
		) {
			val list = state.items
			items(count = list.size, key = { idx -> list[idx].id }) { idx ->
				val item = list[idx]
				when (item) {
					is Chapter -> ChapterListItem(item = item,
						settings = state.settings,
						onOpen = { navigate(Screens.ChapterDetail.of(item.id)) },
						onDelete = { viewModel.deleteChild(item) })

					is Picture -> PictureListItem(
						item = item,
						settings = state.settings,
						onDelete = { viewModel.deleteChild(item) },
						onToggleAll = viewModel::toggleAll
					)

					is Word -> WordListItem(
						item = item,
						settings = state.settings,
						onDelete = { viewModel.deleteChild(item) },
						onToggleAll = viewModel::toggleAll
					)

					else -> throw IllegalStateException("Invalid type ${list[idx].javaClass} in chapter ${state.source.id}")
				}
			}
		}
	}
}

