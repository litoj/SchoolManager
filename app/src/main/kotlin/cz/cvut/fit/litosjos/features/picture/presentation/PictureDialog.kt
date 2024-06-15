package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PictureDialog(
	src: Picture,
	onDismiss: () -> Unit,
	onConfirm: (Picture) -> Unit,
	isNew: Boolean,
) {
	var uri by rememberSaveable { mutableStateOf(src.uri) }

	ItemDialog(src, onDismiss = onDismiss, onConfirm = { onConfirm(Picture(it, uri)) }, header = {
		ItemDialogHeader(
			titleId = if (isNew) R.string.new_picture else R.string.update, iconId = R.drawable.ic_picture
		)
	}) {
		TextField(value = uri,
			singleLine = true,
			onValueChange = { uri = it },
			label = { Text(stringResource(R.string.picture_url)) })
		AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth())
	}
}

@Composable
fun PictureDialog(item: Picture, onDismiss: () -> Unit) {
	val viewModel: PictureDialogViewModel = koinViewModel(
		parameters = { parametersOf(item.isValid()) }, key = item.id.toString()
	)

	PictureDialog(item, onDismiss, { viewModel.save(it, onDismiss) }, !item.isValid())
}