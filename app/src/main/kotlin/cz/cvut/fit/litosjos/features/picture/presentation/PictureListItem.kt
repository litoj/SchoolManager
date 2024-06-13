package cz.cvut.fit.litosjos.features.picture.presentation

import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.ColorizedItemIcon
import cz.cvut.fit.litosjos.core.presentation.DescriptionInfoWrapper
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.settings.presentation.presets.SwipeContainer
import cz.cvut.fit.litosjos.features.settings.theme.Sizes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PictureListItem(
	state: PictureListItemState,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	onDelete: (Item) -> Unit,
	onClick: () -> Unit,
) {
	// FIXME: why do we receive redundant updates, even when the state is the same?
	Log.d("PictureListItem", "child.hasUri: ${state.picture.uri.isNotEmpty()}")

	SwipeContainer(item = state.picture.base, onDelete = onDelete, onUpdate = {
		dialogUpdater { // FIXME: why does this not use the latest state?
			Log.d("PictureListItem", "dialog.hasUri: ${state.picture.uri.isNotEmpty()}")
			PictureDialog(item = state.picture, onDismiss = { dialogUpdater(null) })
		}
	}) { item ->
		Column {
			Row(
				modifier = Modifier
					.clickable(onClick = onClick)
					.background(MaterialTheme.colorScheme.background)
					.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				ColorizedItemIcon(R.drawable.ic_picture, item.passedTests, item.failedTests, state.settings)
				DescriptionInfoWrapper(
					name = item.name,
					description = item.description,
					maxLinesPreview = state.settings.descriptionLineCount,
					dialogUpdater = dialogUpdater,
					modifier = Modifier
						.weight(1f)
						.padding(vertical = Sizes.padding, horizontal = 1.dp),
				) {
					if (state.showImage) AsyncImage(
						model = state.picture.uri,
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


@Composable
fun PictureListItem(
	item: Item,
	sharedViewModel: ListItemSharedViewModel,
	dialogUpdater: (@Composable (() -> Unit)?) -> Unit,
	onDelete: (Item) -> Unit,
) {
	val viewModel: PictureListItemViewModel =
		koinViewModel(parameters = { parametersOf(item, sharedViewModel) }, key = item.id.toString())

	val state by viewModel.state.collectAsStateWithLifecycle()
	Log.d(
		"PictureListItem", "viewmodel: ${viewModel.hashCode()}; state: ${
			state.hashCode()
		}, hasUri: ${state.picture.uri.isNotEmpty()}, $state"
	)
	PictureListItem(
		state = state,
		dialogUpdater = dialogUpdater,
		onDelete = onDelete,
		onClick = viewModel::toggleImage
	)
}