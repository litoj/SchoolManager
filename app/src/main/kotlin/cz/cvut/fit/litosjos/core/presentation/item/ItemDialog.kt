package cz.cvut.fit.litosjos.core.presentation.item

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Dialog
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Icon
import cz.cvut.fit.litosjos.features.settings.theme.Sizes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RowScope.ItemDialogHeader(titleId: Int, iconId: Int) {
	Icon(iconId)
	Text(
		stringResource(id = titleId),
		style = MaterialTheme.typography.titleMedium,
		modifier = Modifier
			.weight(1f)
			.padding(start = Sizes.padding)
	)
}

@Composable
fun ItemDialog(
	state: Item,
	onChange: (Item) -> Unit,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
	header: @Composable RowScope.() -> Unit,
	additionalContent: @Composable ColumnScope.() -> Unit
) {
	Dialog(onDismissRequest = onDismiss, header = header, footer = {
		Button(onClick = onDismiss) {
			Text(stringResource(R.string.cancel))
		}
		Spacer(Modifier.weight(1f))
		Button(onClick = onConfirm) {
			Text(stringResource(R.string.done))
		}
	}) {
		TextField(
			value = state.name,
			onValueChange = { onChange(state.copy(name = it)) },
			label = { Text(stringResource(R.string.name)) },
			singleLine = true,
		)
		TextField(value = state.description,
			onValueChange = { onChange(state.copy(description = it)) },
			label = { Text(stringResource(R.string.description)) })

		additionalContent()
	}
}

@Composable
fun ItemDialog(
	item: Item,
	onDismiss: () -> Unit,
	header: @Composable RowScope.() -> Unit,
) {
	val viewModel: ItemDialogViewModel = koinViewModel(
		parameters = { parametersOf(item) }, key = item.id.toString()
	)

	val state by viewModel.state.collectAsStateWithLifecycle()
	ItemDialog(
		state = state,
		onChange = viewModel::updateState,
		onDismiss = onDismiss,
		onConfirm = { viewModel.save(onDismiss) },
		header = header
	) {}
}