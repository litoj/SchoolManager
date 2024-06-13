package cz.cvut.fit.litosjos.features.word.presentation

import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.word.domain.Word
import cz.cvut.fit.litosjos.features.word.domain.isValid
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WordDialog(
	state: Word,
	onDismiss: () -> Unit,
	onChange: (Word) -> Unit,
	onConfirm: () -> Unit,
	isNew: Boolean,
) {
	ItemDialog(state.base,
		onChange = { onChange(state.copy(base = it)) },
		onDismiss = onDismiss,
		onConfirm = onConfirm,
		header = {
			ItemDialogHeader(
				titleId = if (isNew) R.string.new_word else R.string.update, iconId = R.drawable.ic_word
			)
		}) {
		TextField(value = state.translations,
			singleLine = true,
			onValueChange = { onChange(state.copy(translations = it)) },
			label = { Text(stringResource(R.string.translation)) })
	}

}

@Composable
fun WordDialog(
	item: Word, viewModel: WordDialogViewModel = koinViewModel(
		parameters = { parametersOf(item) }, key = item.base.id.toString()
	), onDismiss: () -> Unit
) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	WordDialog(
		state, onDismiss, viewModel::updateState, { viewModel.save(onDismiss) }, !item.isValid()
	)
}