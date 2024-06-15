package cz.cvut.fit.litosjos.features.word.presentation

import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.word.domain.Word
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WordDialog(
	src: Word,
	onDismiss: () -> Unit,
	onConfirm: (Word) -> Unit,
	isNew: Boolean,
) {
	var translations by rememberSaveable { mutableStateOf(src.translations) }

	ItemDialog(src,
		onDismiss = onDismiss,
		onConfirm = { onConfirm(src.copy(it, translations)) },
		header = {
			ItemDialogHeader(
				titleId = if (isNew) R.string.new_word else R.string.update, iconId = R.drawable.ic_word
			)
		}) {
		TextField(value = translations,
			singleLine = true,
			onValueChange = { translations = it },
			label = { Text(stringResource(R.string.translation)) })
	}

}

@Composable
fun WordDialog(item: Word, onDismiss: () -> Unit) {
	val viewModel: WordDialogViewModel = koinViewModel(
		parameters = { parametersOf(item.isValid()) }, key = item.id.toString()
	)

	WordDialog(item, onDismiss, { viewModel.save(it, onDismiss) }, !item.isValid())
}