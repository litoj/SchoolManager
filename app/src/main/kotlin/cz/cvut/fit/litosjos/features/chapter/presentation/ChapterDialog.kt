package cz.cvut.fit.litosjos.features.chapter.presentation

import androidx.compose.runtime.Composable
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialog
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogHeader
import cz.cvut.fit.litosjos.features.chapter.domain.Chapter

@Composable
fun ChapterDialog(item: Chapter, onDismiss: () -> Unit) {
	ItemDialog(item, onDismiss = onDismiss, header = {
		ItemDialogHeader(
			titleId = if (item.isValid()) R.string.update else R.string.new_chapter,
			iconId = R.drawable.ic_chapter
		)
	})
}