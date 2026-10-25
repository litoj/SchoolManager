package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * The values shown and edited by the settings screen.
 * - every change applies to the backing statics and persists at once.
 * - so nothing is lost on process death, and no save is deferred.
 * - [SettingsViewModel] fills the plain-value constructor from the state.
 */
class SettingsState(
	parse: Boolean = true,
	defFlip: Boolean = true,
	descLinesText: String = ItemRow.DEFAULT_DESC_LINES.toString(),
	doubleTapMsText: String = ItemRow.doubleTapMs.toString(),
	wordSplitText: String = ";",
	clever: Boolean = false,
	amountText: String = "10",
	timeText: String = TestEngine.DEFAULT_TIME_SECONDS.toString(),
) {
	var parse by mutableStateOf(parse)
		private set
	var defFlip by mutableStateOf(defFlip)
		private set
	var descLinesText by mutableStateOf(descLinesText)
		private set
	var doubleTapMsText by mutableStateOf(doubleTapMsText)
		private set
	var wordSplitText by mutableStateOf(wordSplitText)
		private set
	var clever by mutableStateOf(clever)
		private set
	var amountText by mutableStateOf(amountText)
		private set
	var timeText by mutableStateOf(timeText)
		private set

	fun toggleParse() {
		parse = !parse
		ItemRow.parse = parse
		AppSettings.set("parseNames", parse)
	}

	fun toggleDefFlip() {
		defFlip = !defFlip
		ItemRow.defFlip = defFlip
		AppSettings.set("flipWord", defFlip)
	}

	/** Zero stands for the icon-only display. */
	fun setDescLines(text: String) {
		descLinesText = digitsOnly(text)
		descLinesText.toIntOrNull()?.let {
			ItemRow.descLines = it
			AppSettings.set("descLines", it)
		}
	}

	fun descLinesFocusLost() {
		if (descLinesText.toIntOrNull() == null)
			descLinesText = ItemRow.descLines.toString()
	}

	/** Zero stands for a disabled double, since no second tap fits the window. */
	fun setDoubleTapMs(text: String) {
		doubleTapMsText = digitsOnly(text)
		doubleTapMsText.toIntOrNull()?.let {
			ItemRow.doubleTapMs = it
			AppSettings.set("doubleTapMs", it)
		}
	}

	fun doubleTapMsFocusLost() {
		if (doubleTapMsText.toIntOrNull() == null)
			doubleTapMsText = ItemRow.doubleTapMs.toString()
	}

	fun toggleClever() {
		clever = !clever
		TestEngine.isClever = clever
		AppSettings.set("isClever", clever)
	}

	/**
	 * Persists the export word splitter.
	 * - the writer keeps its previous value for anything it cannot parse.
	 * - so only a taken-over text persists.
	 */
	fun setWordSplit(text: String) {
		wordSplitText = text
		WordListWriter.setWordTranslationSplitter(text)
		if (WordListWriter.wordTranslationSplitter == text)
			AppSettings.set("exportWordSplit", text)
	}

	fun wordSplitFocusLost() {
		if (wordSplitText != WordListWriter.wordTranslationSplitter)
			wordSplitText = WordListWriter.wordTranslationSplitter
	}

	fun setAmount(text: String) {
		amountText = digitsOnly(text)
		amountText.toIntOrNull()?.let {
			if (it >= 1) {
				TestEngine.amount = it
				AppSettings.set("testAmount", it)
			}
		}
	}

	fun amountFocusLost() {
		if (needsRestore(amountText))
			amountText = TestEngine.amount.toString()
	}

	fun setTime(text: String) {
		timeText = digitsOnly(text)
		timeText.toIntOrNull()?.let {
			if (it >= 1) {
				TestEngine.setDefaultTime(it)
				AppSettings.set("defaultTestTime", it)
			}
		}
	}

	fun timeFocusLost() {
		if (needsRestore(timeText))
			timeText = TestEngine.getDefaultTime().toString()
	}

	companion object {

		fun digitsOnly(text: String): String = text.filter { it.isDigit() }

		fun needsRestore(text: String): Boolean = text.toIntOrNull()?.let { it >= 1 } != true
	}
}

/**
 * The state survives rotation and the back stack, so edits stay visible on return.
 */
class SettingsViewModel : ViewModel() {
	val state = SettingsState(
		parse = ItemRow.parse,
		defFlip = ItemRow.defFlip,
		descLinesText = ItemRow.descLines.toString(),
		doubleTapMsText = ItemRow.doubleTapMs.toString(),
		wordSplitText = WordListWriter.wordTranslationSplitter,
		clever = TestEngine.isClever,
		amountText = TestEngine.amount.toString(),
		timeText = TestEngine.getDefaultTime().toString(),
	)
}
