package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.picture.domain.isValid
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PictureDialog(
	state: Picture,
	onDismiss: () -> Unit,
	onChange: (Picture) -> Unit,
	onConfirm: () -> Unit,
	isNew: Boolean,
) {
	ItemDialog(state.base,
		onChange = { onChange(state.copy(base = it)) },
		onDismiss = onDismiss,
		onConfirm = onConfirm,
		header = {
			ItemDialogHeader(
				titleId = if (isNew) R.string.new_picture else R.string.update,
				iconId = R.drawable.ic_picture
			)
		}) {
		TextField(value = state.uri,
			singleLine = true,
			onValueChange = { onChange(state.copy(uri = it)) },
			label = { Text(stringResource(R.string.picture_url)) })
		AsyncImage(model = state.uri, contentDescription = null, modifier = Modifier.fillMaxWidth())
	}

}

@Composable
fun PictureDialog(
	item: Picture, viewModel: PictureDialogViewModel = koinViewModel(
		parameters = { parametersOf(item) }, key = item.base.id.toString()
	), onDismiss: () -> Unit
) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	PictureDialog(
		state, onDismiss, viewModel::updateState, { viewModel.save(onDismiss) }, !item.isValid()
	)
}