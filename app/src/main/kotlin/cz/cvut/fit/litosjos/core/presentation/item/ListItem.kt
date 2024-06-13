package cz.cvut.fit.litosjos.core.presentation.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.ColorizedItemIcon
import cz.cvut.fit.litosjos.core.presentation.DescriptionDialog
import cz.cvut.fit.litosjos.core.presentation.DescriptionInfoWrapper
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.theme.Sizes

@Composable
fun ListItem(
	item: Item,
	icon: Int,
	settings: Settings,
	dialogUpdater: ((@Composable () -> Unit)?) -> Unit,
	modifier: Modifier = Modifier
) {
	Column {
		Row(
			modifier = modifier
				.background(MaterialTheme.colorScheme.background)
				.fillMaxWidth()
				.height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically
		) {
			ColorizedItemIcon(icon, item.passedTests, item.failedTests, settings)
			DescriptionInfoWrapper(
				name = item.name,
				description = item.description,
				maxLinesPreview = settings.descriptionLineCount,
				dialogUpdater = dialogUpdater,
				modifier = Modifier
					.weight(1f)
					.padding(vertical = Sizes.padding, horizontal = 1.dp),
			) {
				Text(text = item.name, modifier = it)
			}
		}
		HorizontalDivider()
	}
}

@Composable
fun ListItem(
	item: Item, icon: Int, sharedViewModel: ListItemSharedViewModel, modifier: Modifier = Modifier
) {
	val state by sharedViewModel.state.collectAsStateWithLifecycle()
	ListItem(
		item, icon, state.settings, { sharedViewModel.update(state.copy(dialog = it)) }, modifier
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun ItemPreview() {
	val max = 4
	val settings = Settings(descriptionLineCount = max / 2)
	Column {
		for (p in 0..max) {
			ListItem(Item(
				name = "Item $p",
				description = "\nNewLine, haha".repeat(p),
				passedTests = p,
				failedTests = max - p
			), R.drawable.ic_chapter, settings, { })
		}
		ListItem(Item(
			name = "Item no desc, but very long name spanning multiple lines spanning several lines",
			description = "Description so long I could have managed to copy and paste some lorem ipsum which may had been a better choice",
		),
			R.drawable.ic_chapter,
			Settings(descriptionLineCount = 2, testSuccessColorizeBackground = true),
			{})
	}
}

@Preview
@Composable
fun DescriptionDialogPreview() {
	DescriptionDialog(
		"Name",
		"Description spanning several lines, so long I could have managed to copy and paste some lorem ipsum which may had been a better choice"
	) {}
	DescriptionDialog(
		"Name", "Short dialog"
	) {}
}