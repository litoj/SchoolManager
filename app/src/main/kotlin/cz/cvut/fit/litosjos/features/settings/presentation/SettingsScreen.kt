package cz.cvut.fit.litosjos.features.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.features.chapter.presentation.ChapterListItem
import cz.cvut.fit.litosjos.features.picture.presentation.PictureListItem
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.presentation.presets.Icon
import cz.cvut.fit.litosjos.features.settings.theme.Sizes
import cz.cvut.fit.litosjos.features.subject.presentation.SubjectListItem
import cz.cvut.fit.litosjos.features.word.presentation.WordListItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun Preview(
	state: SettingsScreenState, onQuery: (String) -> Unit, modifier: Modifier
) {
	// kinda hacky but there's no other way to do distinguish local changes from settings change
	var toggled by remember { mutableStateOf(state.settings.showTranslated) }
	var original by remember { mutableStateOf(state.settings.showTranslated) }
	if (original != state.settings.showTranslated) {
		toggled = state.settings.showTranslated
		original = state.settings.showTranslated
	}
	val settings = state.settings.copy(showTranslated = toggled)

	Column(
		Modifier
			.clip(RoundedCornerShape(Sizes.roundness))
			.background(MaterialTheme.colorScheme.primaryContainer)
			.padding(Sizes.padding)
			.background(MaterialTheme.colorScheme.surface)
	) {
		TopAppBar(title = {
			TextField(
				label = { Text(stringResource(R.string.settings_preview_query)) },
				value = state.previewQuery,
				onValueChange = onQuery,
				textStyle = MaterialTheme.typography.headlineSmall // smaller to better fit
			)
		}, actions = {
			IconButton(onClick = {}) {
				Icon(Icons.Default.MoreVert, contentDescriptionId = R.string.new_item)
			}
		})
		// TODO: currently no support for displaying parent description

		Column(modifier) {
			SubjectListItem(
				item = state.previewData.parent,
				settings = settings,
				onOpen = {},
				onDelete = {},
			)

			ChapterListItem(
				item = state.previewData.chapter,
				settings = settings,
				onOpen = {},
				onDelete = {},
			)

			PictureListItem(
				item = state.previewData.picture,
				settings = settings,
				onDelete = {},
				onToggleAll = { toggled = it },
			)
			WordListItem(
				item = state.previewData.word,
				settings = settings,
				onDelete = {},
				onToggleAll = { toggled = it },
			)
		}
	}
}

@Composable
fun BooleanOption(
	labelId: Int, value: Boolean, onToggle: (Boolean) -> Unit,
) {
	Row(verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.clickable { onToggle(!value) }
			.padding(Sizes.padding)) {
		Text(stringResource(labelId))
		Spacer(Modifier.weight(1f))
		Switch(checked = value, onCheckedChange = onToggle)
	}
}

@Composable
fun NumberOption(
	labelId: Int, value: Float, onUpdate: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
) {
	Column(modifier = Modifier.padding(Sizes.padding)) {
		Text(stringResource(labelId))
		Slider(value = value, onValueChange = onUpdate, valueRange = range, steps = 20)
	}
}

@Composable
fun SettingsList(
	settings: Settings, onUpdate: (Settings) -> Unit,
) {
	Column(
		modifier = Modifier.padding(Sizes.padding)
	) {
		Text(
			text = stringResource(R.string.settings_section_general),
			style = MaterialTheme.typography.headlineSmall
		)
		BooleanOption(labelId = R.string.settings_toggle_all,
			value = settings.toggleAll,
			onToggle = { onUpdate(settings.copy(toggleAll = it)) })
		BooleanOption(labelId = R.string.settings_show_translated,
			value = settings.showTranslated,
			onToggle = { onUpdate(settings.copy(showTranslated = it)) })
		NumberOption(
			labelId = R.string.settings_description_line_count,
			value = settings.descriptionLineCount.toFloat(),
			onUpdate = {
				onUpdate(settings.copy(descriptionLineCount = if (it == 20f) Int.MAX_VALUE else it.toInt()))
			},
			range = 0f..20f
		)
	}

	HorizontalDivider()

	Column(modifier = Modifier.padding(Sizes.padding)) {
		Text(
			text = stringResource(R.string.settings_section_test_success_rate),
			style = MaterialTheme.typography.headlineSmall
		)
		NumberOption(
			labelId = R.string.settings_test_success_color_intensity,
			value = settings.testSuccessColorIntensity.toFloat(),
			onUpdate = { onUpdate(settings.copy(testSuccessColorIntensity = it.toInt())) },
			range = 0f..255f
		)
		BooleanOption(labelId = R.string.settings_test_success_colorize_background,
			value = settings.testSuccessColorizeBackground,
			onToggle = { onUpdate(settings.copy(testSuccessColorizeBackground = it)) })
		BooleanOption(labelId = R.string.settings_untested_item_colorize,
			value = settings.untestedItemColorize,
			onToggle = { onUpdate(settings.copy(untestedItemColorize = it)) })
	}
}

@Composable
fun SettingsScreen(onBackPress: () -> Unit) {
	val viewModel: SettingsScreenViewModel = koinViewModel()

	val state by viewModel.state.collectAsStateWithLifecycle()

	val importLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { if (it != null) viewModel.import(it) }
	val exportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain")
	) { if (it != null) viewModel.export(it) }
	val appName = stringResource(R.string.app_name)

	BackHandler {
		viewModel.save()
		onBackPress()
	}

	Scaffold(topBar = {
		TopAppBar(title = {
			Text(
				text = stringResource(id = R.string.settings),
				style = MaterialTheme.typography.headlineMedium
			)
		})
	}) {
		Column(
			Modifier
				.padding(it)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = Sizes.padding)
		) {
			Preview(
				state = state,
				onQuery = viewModel::query,
				modifier = Modifier
					.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 1 / 3)
					.verticalScroll(rememberScrollState())
			)

			HorizontalDivider(Modifier.padding(top = Sizes.padding))

			SettingsList(settings = state.settings, onUpdate = viewModel::update)

			HorizontalDivider()

			Button(onClick = { importLauncher.launch(arrayOf("text/plain")) }) {
				Text(stringResource(R.string.import_settings))
			}

			Button(onClick = { exportLauncher.launch("$appName settings.txt") }) {
				Text(stringResource(R.string.export_settings))
			}
		}
	}
}