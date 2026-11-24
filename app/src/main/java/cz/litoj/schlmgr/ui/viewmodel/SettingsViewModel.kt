package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import cz.litoj.schlmgr.ui.component.CrumbSize
import cz.litoj.schlmgr.ui.component.SuccessBand
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.component.NumberInput
import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * The values shown and edited by the settings screen.
 * - every change applies to the backing statics and persists at once.
 * - so nothing is lost on process death, and no save is deferred.
 * - [SettingsViewModel] fills the display values from current state.
 */
class SettingsState(
	private val settings: Settings,
	parse: Boolean = true,
	defFlip: Boolean = true,
	searchDefFlip: Boolean = true,
	searchShowPath: Boolean = true,
	descLinesText: String = ItemRow.DEFAULT_DESC_LINES.toString(),
	doubleTapMsText: String = ItemRow.doubleTapMs.toString(),
	wordSplitText: String = ";",
	requireVariants: Boolean = false,
	ignoredMistakesText: String = TestEngine.ignoredMistakesText,
	amountText: String = "10",
	timeText: String = TestEngine.getSecondsPerChar().toString(),
	defaultReverse: Boolean = false,
	searchDiacritics: Boolean = false,
	selectionOrder: List<SelectionAction> = ActionConfig.DEFAULT_ORDER,
	crumbSize: CrumbSize = CrumbSize.current,
	bandIntensity: Int = SuccessBand.DEFAULT_INTENSITY,
) {
	var parse by mutableStateOf(parse)
		private set
	var defFlip by mutableStateOf(defFlip)
		private set
	var searchDefFlip by mutableStateOf(searchDefFlip)
		private set
	var searchShowPath by mutableStateOf(searchShowPath)
		private set
	var crumbSize by mutableStateOf(crumbSize)
		private set
	var bandIntensity by mutableStateOf(bandIntensity)
		private set
	var descLinesText by mutableStateOf(descLinesText)
		private set
	var doubleTapMsText by mutableStateOf(doubleTapMsText)
		private set
	var wordSplitText by mutableStateOf(wordSplitText)
		private set
	var requireVariants by mutableStateOf(requireVariants)
		private set
	var ignoredMistakesText by mutableStateOf(ignoredMistakesText)
		private set
	val ignoredMistakes: List<String>
		get() = ignoredMistakesText.lines().filter { it.isNotEmpty() }
	var amountText by mutableStateOf(amountText)
		private set
	var timeText by mutableStateOf(timeText)
		private set
	/** The saved direction of a fresh test, where false asks for the word. */
	var defaultReverse by mutableStateOf(defaultReverse)
		private set
	var searchDiacritics by mutableStateOf(searchDiacritics)
		private set

	/** The ordered selection actions, where the first four applicable hold the strip slots. */
	var selectionOrder by mutableStateOf(selectionOrder)
		private set

	fun toggleParse() {
		parse = !parse
		applyRowOptions()
		settings.set("parseNames", parse)
	}

	fun toggleDefFlip() {
		defFlip = !defFlip
		applyRowOptions()
		settings.set("flipWord", defFlip)
	}

	fun toggleSearchDefFlip() {
		searchDefFlip = !searchDefFlip
		applyRowOptions()
		settings.set("flipSearch", searchDefFlip)
	}

	fun toggleSearchShowPath() {
		searchShowPath = !searchShowPath
		applyRowOptions()
		settings.set("searchShowPath", searchShowPath)
	}

	fun cycleCrumbSize() {
		crumbSize = CrumbSize.next()
		CrumbSize.select(settings, crumbSize)
	}

	/** The band re-renders live, so the list darkens while the slider moves. */
	fun changeBandIntensity(percent: Int) {
		bandIntensity = percent
		SuccessBand.setPeak(settings, percent)
	}

	/** Zero stands for the icon-only display. */
	fun setDescLines(text: String) {
		descLinesText = digitsOnly(text)
		descLinesText.toIntOrNull()?.let {
			applyRowOptions()
			settings.set("descLines", it)
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
			applyRowOptions()
			settings.set("doubleTapMs", it)
		}
	}

	fun doubleTapMsFocusLost() {
		if (doubleTapMsText.toIntOrNull() == null)
			doubleTapMsText = ItemRow.doubleTapMs.toString()
	}

	fun toggleRequireVariants() {
		requireVariants = !requireVariants
		TestEngine.applyRequireAllVariants(requireVariants)
		settings.set("testRequireAllVariants", requireVariants)
	}

	fun setIgnoredMistakes(text: String) {
		ignoredMistakesText = text
		TestEngine.applyIgnoredMistakes(text)
		settings.set("testIgnoredMistakes", text)
	}

	fun setIgnoredMistake(index: Int, text: String) {
		val entries = ignoredMistakes.toMutableList()
		require(index in 0..entries.size)
		if (text.isEmpty()) {
			if (index < entries.size) entries.removeAt(index)
		} else if (index == entries.size) entries.add(text)
		else entries[index] = text
		setIgnoredMistakes(entries.joinToString("\n"))
	}

	/** A fresh setup reads the engine, so the next test takes the new default. */
	fun toggleDefaultReverse() {
		defaultReverse = !defaultReverse
		TestEngine.applyReversed(defaultReverse)
		settings.set("reverseTest", defaultReverse)
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
			settings.set("exportWordSplit", text)
	}

	fun wordSplitFocusLost() {
		if (wordSplitText != WordListWriter.wordTranslationSplitter)
			wordSplitText = WordListWriter.wordTranslationSplitter
	}

	fun setAmount(text: String) {
		amountText = digitsOnly(text)
		amountText.toIntOrNull()?.let {
			if (it >= 1) {
				TestEngine.applyOptions(TestEngine.isClever, it)
				settings.set("testAmount", it)
			}
		}
	}

	fun amountFocusLost() {
		if (needsRestore(amountText))
			amountText = TestEngine.amount.toString()
	}

	fun setTime(text: String) {
		timeText = NumberInput.decimal(text)
		NumberInput.amount(timeText)?.let {
			TestEngine.setSecondsPerChar(it)
			settings.set("testSecondsPerChar", it)
		}
	}

	fun timeFocusLost() {
		if (NumberInput.amount(timeText) == null)
			timeText = TestEngine.getSecondsPerChar().toString()
	}

	/** The search reads the flag per run, so nothing else needs to react. */
	fun toggleSearchDiacritics() {
		searchDiacritics = !searchDiacritics
		settings.set("searchNoDiacritics", searchDiacritics)
	}

	/** Moves one action in the order. */
	fun moveSelectionAction(from: Int, to: Int) {
		selectionOrder = ActionConfig(selectionOrder).moved(from, to).order
		ActionConfig.save(settings, selectionOrder)
	}

	/** The row options move through the one door of ItemRow. */
	private fun applyRowOptions() {
		ItemRow.applyOptions(parse, defFlip, searchDefFlip,
			descLinesText.toIntOrNull() ?: ItemRow.descLines,
			doubleTapMsText.toIntOrNull() ?: ItemRow.doubleTapMs,
			searchShowPath)
	}

	companion object {

		fun digitsOnly(text: String): String = text.filter { it.isDigit() }

		fun needsRestore(text: String): Boolean = text.toIntOrNull()?.let { it >= 1 } != true
	}
}

/**
 * The state survives rotation and the back stack, so edits stay visible on return.
 */
class SettingsViewModel(private val settings: Settings) : ViewModel() {
	private val config = ActionConfig.load(settings)

	val state = SettingsState(
		settings = settings,
		parse = ItemRow.parse,
		defFlip = ItemRow.defFlip,
		searchDefFlip = ItemRow.searchDefFlip,
		searchShowPath = ItemRow.searchShowPath,
		descLinesText = ItemRow.descLines.toString(),
		doubleTapMsText = ItemRow.doubleTapMs.toString(),
		wordSplitText = WordListWriter.wordTranslationSplitter,
		requireVariants = settings.getBool("testRequireAllVariants", false),
		ignoredMistakesText = TestEngine.ignoredMistakesText,
		amountText = TestEngine.amount.toString(),
		timeText = TestEngine.getSecondsPerChar().toString(),
		defaultReverse = TestEngine.isReversed,
		searchDiacritics = settings.getBool("searchNoDiacritics", false),
		selectionOrder = config.order,
		bandIntensity = SuccessBand.intensity,
	)
}
