package cz.litoj.schlmgr.ui.screen.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.BareTextField
import cz.litoj.schlmgr.ui.component.BigHeader
import cz.litoj.schlmgr.ui.component.SectionHeader
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.SettingChoiceRow
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
			TopBarNav(up = true, onNavigate = onBack)
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
		SettingSwitchRow(stringResource(R.string.setts_him_parse), state.parse, state::toggleParse)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped), state.defFlip, state::toggleDefFlip)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped_search), state.searchDefFlip, state::toggleSearchDefFlip)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_desc_lines), state.descLinesText, state::setDescLines,
		) { state.descLinesFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_double_tap_ms), state.doubleTapMsText, state::setDoubleTapMs,
		) { state.doubleTapMsFocusLost(); keyboard?.hide() }
		HairlineDivider()
		WordSplitRow(state)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_search_diacritics), state.searchDiacritics, state::toggleSearchDiacritics)
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
		) { state.amountFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_test_time), state.timeText, state::setTime,
		) { state.timeFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingChoiceRow(
			stringResource(R.string.setts_test_direction),
			stringResource(if (state.defaultReverse) R.string.test_dir_reverse else R.string.test_dir_normal),
			state::toggleDefaultReverse,
		)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_test_clever), state.clever, state::toggleClever)
	}
}

@Composable
private fun WordSplitRow(state: SettingsState) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			stringResource(R.string.setts_word_splitter),
			Modifier.weight(1f),
			style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
		)
		BareTextField(
			value = state.wordSplitText,
			onValueChange = state::setWordSplit,
			fontSize = 18.sp,
			onFocusLost = state::wordSplitFocusLost,
		)
	}
}
