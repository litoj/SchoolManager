package cz.cvut.fit.litosjos.features.settings.presentation.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.features.settings.theme.Sizes

@Composable
fun Dialog(
	onDismissRequest: () -> Unit,
	header: @Composable RowScope.() -> Unit,
	footer: @Composable RowScope.() -> Unit = {
		Button(onClick = onDismissRequest) {
			Text(stringResource(R.string.ok))
		}
	},
	content: @Composable ColumnScope.() -> Unit
) {
	Dialog(onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.padding(Sizes.padding)
				.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.roundness))
				.width(IntrinsicSize.Max)
		) {
			Row(Modifier.padding(Sizes.padding), content = header)
			HorizontalDivider(Modifier.padding(horizontal = Sizes.padding / 2))
			Column(
				modifier = Modifier
					.padding(Sizes.padding)
					.verticalScroll(rememberScrollState())
					.weight(weight = 1f, fill = false), content = content
			)
			Row(
				Modifier
					.padding(bottom = Sizes.padding, start = Sizes.padding, end = Sizes.padding)
					.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = footer
			)
		}
	}
}