package cz.litoj.schlmgr.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.BareTextField
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import cz.litoj.schlmgr.ui.component.SettingSwitchRow
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

private val HeaderBackground @Composable get() = colorResource(R.color.colorSectionHeader)

/**
 * The settings screen — the top bar above the settings list
 * (interface switches, the export splitter field, the test defaults).
 * - every edit applies and persists at once through [SettingsState].
 *
 * @param openDrawer the hamburger press — the shell owns the drawer
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, openDrawer: () -> Unit) {
	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(onNavigate = openDrawer)
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
		SectionHeader(stringResource(R.string.setts_interface))
		SettingSwitchRow(stringResource(R.string.setts_him_parse), state.parse, state::toggleParse)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped), state.defFlip, state::toggleDefFlip)
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_allflip), state.flipAllOnClick, state::toggleFlipAllOnClick)
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_desc_lines), state.descLinesText, state::setDescLines,
		) { state.descLinesFocusLost(); keyboard?.hide() }
		HairlineDivider()
		WordSplitRow(state)
		// no resource exists for the "Test" section label — it stays a literal
		SectionHeader("Test")
		SettingNumberRow(
			stringResource(R.string.setts_test_amount), state.amountText, state::setAmount,
		) { state.amountFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingNumberRow(
			stringResource(R.string.setts_test_time), state.timeText, state::setTime,
		) { state.timeFocusLost(); keyboard?.hide() }
		HairlineDivider()
		SettingSwitchRow(stringResource(R.string.setts_test_clever), state.clever, state::toggleClever)
	}
}

/** The translucent section title bars ("Interface" / "Test"). */
@Composable
private fun SectionHeader(title: String) {
	Text(
		title,
		Modifier
			.fillMaxWidth()
			.background(HeaderBackground)
			.padding(horizontal = 15.dp, vertical = 2.dp),
		style = TextStyle(fontSize = 15.sp, color = DefaultTextColor),
	)
}

/** The export word splitter — description and a free-text field for the exact string. */
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


