package cz.litoj.schlmgr.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.BareTextField
import cz.litoj.schlmgr.ui.component.BigHeader
import cz.litoj.schlmgr.ui.component.SectionHeader
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.SettingCaption
import cz.litoj.schlmgr.ui.component.SettingChoiceRow
import cz.litoj.schlmgr.ui.component.SettingFieldRow
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import cz.litoj.schlmgr.ui.component.SettingSwitchRow
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

/**
 * The settings screen: the top bar above the settings list.
 * - the list holds the interface switches, the export splitter field and the test defaults.
 * - every edit applies and persists at once through [SettingsState].
 *
 * @param onBack the back arrow press, the host owns the back stack
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(
				up = true,
				onNavigate = onBack,
				contentDescription = stringResource(R.string.back),
			)
			TopBarTitle(stringResource(R.string.menu_settings))
		}
		SettingsList(viewModel.state, Modifier.weight(1f).headerShadow())
	}
}

@Composable
private fun SettingsList(state: SettingsState, modifier: Modifier = Modifier) {
	val keyboard = LocalSoftwareKeyboardController.current
	Column(
		modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState()),
	) {
		BigHeader(stringResource(R.string.setts_explorer), R.drawable.ic_subject, colorResource(R.color.colorOnHeader))
		SettingSwitchRow(
			stringResource(R.string.setts_him_parse), state.parse, state::toggleParse,
			caption = stringResource(R.string.setts_him_parse_hint),
		)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_him_flipped), state.defFlip, state::toggleDefFlip,
			caption = stringResource(R.string.setts_him_flipped_hint),
		)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_him_flipped_search), state.searchDefFlip, state::toggleSearchDefFlip,
			caption = stringResource(R.string.setts_him_flipped_search_hint),
		)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_search_show_path), state.searchShowPath, state::toggleSearchShowPath,
			caption = stringResource(R.string.setts_search_show_path_hint),
		)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_desc_lines), state.descLinesText, state::setDescLines,
			{ state.descLinesFocusLost(); keyboard?.hide() },
			caption = stringResource(R.string.setts_desc_lines_hint),
		)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_double_tap_ms), state.doubleTapMsText, state::setDoubleTapMs,
			{ state.doubleTapMsFocusLost(); keyboard?.hide() },
			caption = stringResource(R.string.setts_double_tap_ms_hint),
		)
		HairlineDivider()
		WordSplitRow(state)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_search_diacritics), state.searchDiacritics,
			state::toggleSearchDiacritics,
			caption = stringResource(R.string.setts_search_diacritics_hint),
		)
		// the selection actions are a subsection of the explorer, since they act on its list
		SectionHeader(stringResource(R.string.setts_selection_bar), level = 0)
		Text(
			stringResource(R.string.setts_bar_hint),
			Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
			style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
		)
		SelectionBarEditor(state)
		BigHeader(stringResource(R.string.help_test), R.drawable.ic_test, colorResource(R.color.colorOnHeader))
		SettingNumberRow(
			stringResource(R.string.setts_test_amount), state.amountText, state::setAmount,
			onFocusLost = { state.amountFocusLost(); keyboard?.hide() },
		)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_test_time), state.timeText, state::setTime,
			onFocusLost = { state.timeFocusLost(); keyboard?.hide() },
			keyboardType = KeyboardType.Decimal,
		)
		HairlineDivider()
		SettingChoiceRow(
			stringResource(R.string.setts_test_direction),
			stringResource(if (state.defaultReverse) R.string.test_dir_reverse else R.string.test_dir_normal),
			state::toggleDefaultReverse,
			caption = stringResource(R.string.setts_test_direction_hint),
		)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_test_clever), state.clever, state::toggleClever,
			caption = stringResource(R.string.setts_test_clever_hint),
		)
		HairlineDivider()
		SettingSwitchRow(
			stringResource(R.string.setts_test_variants), state.requireVariants, state::toggleRequireVariants,
			caption = stringResource(R.string.setts_test_variants_hint),
		)
		HairlineDivider()
		IgnoredMistakesRow(state)
		Spacer(Modifier.height(24.dp))
	}
}

@Composable
private fun IgnoredMistakesRow(state: SettingsState) {
	val fields = state.ignoredMistakes + ""
	// one requester per field, so Enter walks the row instead of closing the keyboard
	val fieldFocus = fields.map { remember { FocusRequester() } }
	val keyboard = LocalSoftwareKeyboardController.current
	Column(Modifier.fillMaxWidth().padding(5.dp)) {
		Text(
			stringResource(R.string.setts_test_ignored),
			Modifier.fillMaxWidth().clickable {
				fieldFocus.first().requestFocus()
				keyboard?.show()
			},
			style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
		)
		SettingCaption(stringResource(R.string.setts_test_ignored_hint))
		Row(
			Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			fields.forEachIndexed { index, value ->
				BareTextField(
					value = value,
					onValueChange = { state.setIgnoredMistake(index, it) },
					fontSize = 18.sp,
					imeAction = ImeAction.Next,
					onImeAction = { fieldFocus.getOrNull(index + 1)?.requestFocus() },
					modifier = Modifier.testTag("ignored_mistake_$index"),
					focusRequester = fieldFocus[index],
				)
			}
		}
	}
}

@Composable
private fun WordSplitRow(state: SettingsState) {
	SettingFieldRow(
		stringResource(R.string.setts_word_splitter),
		stringResource(R.string.setts_word_splitter_hint),
	) { focus ->
		BareTextField(
			value = state.wordSplitText,
			onValueChange = state::setWordSplit,
			fontSize = 18.sp,
			onFocusLost = state::wordSplitFocusLost,
			focusRequester = focus,
		)
	}
}
