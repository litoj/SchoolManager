package cz.cvut.fit.litosjos.features.subject.presentation

import androidx.compose.runtime.Composable
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.isValid
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader

@Composable
fun SubjectDialog(item: Item, onDismiss: () -> Unit) {
	ItemDialog(item, onDismiss = onDismiss, header = {
		ItemDialogHeader(
			titleId = if (item.isValid()) R.string.update else R.string.new_subject,
			iconId = R.drawable.ic_subject
		)
	})
}