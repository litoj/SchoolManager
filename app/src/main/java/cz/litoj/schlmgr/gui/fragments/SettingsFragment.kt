package cz.litoj.schlmgr.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.gui.AppSettings
import cz.litoj.schlmgr.gui.components.BareTextField
import cz.litoj.schlmgr.gui.Controller
import cz.litoj.schlmgr.gui.Controller.ControlListener
import cz.litoj.schlmgr.gui.list.HierarchyItemModel

import cz.litoj.schlmgr.gui.engine.IOSystem.SimpleWriter
import cz.litoj.schlmgr.gui.engine.testing.Test

private val TextColor = Color(0xDD000000)
private val LabelColor = Color(0xFF000000)
private val HeaderBackground = Color(0x28888888)
private val DividerColor = Color(0x38888888)
private val Accent = Color(0xFF559915)



/**
 * The values shown and edited by the settings screen. Every change is applied to the
 * backing statics and persisted immediately, so nothing is lost on process death and
 * there is no crash-prone deferred save (the old screen saved in onDestroy).
 *
 * The plain-value constructor is used by previews; [SettingsViewModel] fills it from
 * the actual program state.
 */
class SettingsState(
	parse: Boolean = true,
	defFlip: Boolean = true,
	flipAllOnClick: Boolean = false,
	showDesc: Boolean = false,
	wordSplitText: String = ";",
	clever: Boolean = false,
	testPic: Boolean = false,
	amountText: String = "10",
	timeText: String = "18",
) {
	var parse by mutableStateOf(parse)
		private set
	var defFlip by mutableStateOf(defFlip)
		private set
	var flipAllOnClick by mutableStateOf(flipAllOnClick)
		private set
	var showDesc by mutableStateOf(showDesc)
		private set
	var wordSplitText by mutableStateOf(wordSplitText)
		private set
	var clever by mutableStateOf(clever)
		private set
	var testPic by mutableStateOf(testPic)
		private set
	var amountText by mutableStateOf(amountText)
		private set
	var timeText by mutableStateOf(timeText)
		private set

	fun toggleParse() {
		parse = !parse
		HierarchyItemModel.setParse(parse)
	}

	fun toggleDefFlip() {
		defFlip = !defFlip
		HierarchyItemModel.setDefFlip(defFlip)
	}

	fun toggleFlipAllOnClick() {
		flipAllOnClick = !flipAllOnClick
		HierarchyItemModel.setFlipAllOnClick(flipAllOnClick)
	}

	fun toggleShowDesc() {
		showDesc = !showDesc
		HierarchyItemModel.setShowDesc(showDesc)
	}

	fun toggleClever() {
		clever = !clever
		Test.setClever(clever)
		AppSettings.set("isClever", clever)
	}

	fun toggleTestPic() {
		testPic = !testPic
		AppSettings.set("defaultTestTypePicture", testPic)
	}

	/** Persists the export word splitter if the text is a usable value. */
	fun setWordSplit(text: String) {
		wordSplitText = text
		if (text.isNotEmpty()) {
			SimpleWriter.setWordSplitter(text)
			AppSettings.set("exportWordSplit", text)
		}
	}

	/** Restores the last persisted splitter when the text was emptied. */
	fun wordSplitFocusLost() {
		if (wordSplitText.isEmpty())
			wordSplitText = SimpleWriter.getWordSplitter()
	}

	/** Persists the amount if the text parses to a usable value. */
	fun setAmount(text: String) {
		amountText = text.filter { it.isDigit() }
		amountText.toIntOrNull()?.let {
			if (it >= 1) {
				Test.setAmount(it)
				AppSettings.set("testAmount", it)
			}
		}
	}

	/** Restores the last persisted amount when the text is empty or unusable. */
	fun amountFocusLost() {
		if (amountText.toIntOrNull()?.let { it >= 1 } != true)
			amountText = Test.getAmount().toString()
	}

	/** Persists the time per item if the text parses to a usable value. */
	fun setTime(text: String) {
		timeText = text.filter { it.isDigit() }
		timeText.toIntOrNull()?.let {
			if (it >= 1) {
				Test.setDefaultTime(it)
				AppSettings.set("defaultTestTime", it)
			}
		}
	}

	/** Restores the last persisted time when the text is empty or unusable. */
	fun timeFocusLost() {
		if (timeText.toIntOrNull()?.let { it >= 1 } != true)
			timeText = Test.getDefaultTime().toString()
	}
}

/**
 * Loads the current values from the program state once; survives rotation and the
 * back stack, so edits stay visible when returning to the screen.
 */
class SettingsViewModel : ViewModel() {
	val state = SettingsState(
		parse = HierarchyItemModel.parse,
		defFlip = HierarchyItemModel.defFlip,
		flipAllOnClick = HierarchyItemModel.flipAllOnClick,
		showDesc = HierarchyItemModel.show_desc,
		wordSplitText = SimpleWriter.getWordSplitter(),
		clever = Test.isClever(),
		testPic = AppSettings.getBool("defaultTestTypePicture", false),
		amountText = Test.getAmount().toString(),
		timeText = Test.getDefaultTime().toString(),
	)
}

class SettingsFragment : Fragment(), ControlListener {

	private val viewModel: SettingsViewModel by viewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent { SettingsScreen(viewModel.state) }
	}

	override fun onResume() {
		Controller.setCurrentControl(this, 0, false, false)
		super.onResume()
	}
}

@Composable
private fun SettingsScreen(state: SettingsState) {
	val keyboard = LocalSoftwareKeyboardController.current
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState()),
	) {
		SectionHeader(stringResource(R.string.setts_interface))
		SettingSwitchRow(stringResource(R.string.setts_him_parse), state.parse, state::toggleParse)
		SettingDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_flipped), state.defFlip, state::toggleDefFlip)
		SettingDivider()
		SettingSwitchRow(stringResource(R.string.setts_him_allflip), state.flipAllOnClick, state::toggleFlipAllOnClick)
		SettingDivider()
		SettingSwitchRow(stringResource(R.string.setts_show_desc), state.showDesc, state::toggleShowDesc)
		SettingDivider()
		WordSplitRow(state)
		SectionHeader("Test")
		NumberRow(
			stringResource(R.string.setts_test_amount), state.amountText, state::setAmount,
		) { state.amountFocusLost(); keyboard?.hide() }
		SettingDivider()
		NumberRow(
			stringResource(R.string.setts_test_time), state.timeText, state::setTime,
		) { state.timeFocusLost(); keyboard?.hide() }
		SettingDivider()
		SettingSwitchRow(stringResource(R.string.setts_test_clever), state.clever, state::toggleClever)
		SettingDivider()
		SettingSwitchRow(stringResource(R.string.setts_test_type), state.testPic, state::toggleTestPic)
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
		style = TextStyle(fontSize = 15.sp, color = TextColor),
	)
}

/** A label with a trailing switch; the whole row toggles, like the old Switch rows. */
@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggle)
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, color = TextColor))
		Switch(
			checked = checked,
			onCheckedChange = null,
			colors = SwitchDefaults.colors(
				checkedThumbColor = Color.White,
				checkedTrackColor = Accent,
			),
		)
	}
}

/** The export word splitter: description and a free-text field for the exact string. */
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
			style = TextStyle(fontSize = 18.sp, color = TextColor),
		)
		BareTextField(
			value = state.wordSplitText,
			onValueChange = state::setWordSplit,
			fontSize = 18.sp,
			onFocusLost = state::wordSplitFocusLost,
		)
	}
}

/** A label with a trailing digits-only number field; empty input restores on focus loss. */
@Composable
private fun NumberRow(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	onFocusLost: () -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, color = LabelColor))
		BareTextField(
			value = value,
			onValueChange = onValueChange,
			fontSize = 18.sp,
			keyboardType = KeyboardType.Number,
			onFocusLost = onFocusLost,
		)
	}
}

@Composable
private fun SettingDivider() {
	Divider(color = DividerColor)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
	SettingsScreen(SettingsState())
}
