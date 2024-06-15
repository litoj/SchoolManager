package cz.cvut.fit.litosjos.features.subject.presentation

import androidx.compose.runtime.Composable
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.subject.domain.Subject

@Composable
fun SubjectDialog(item: Subject, onDismiss: () -> Unit) {
	ItemDialog(item, onDismiss = onDismiss, header = {
		ItemDialogHeader(
			titleId = if (item.isValid()) R.string.update else R.string.new_subject,
			iconId = R.drawable.ic_subject
		)
	})
}