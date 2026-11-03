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
	searchDefFlip: Boolean = true,
	descLinesText: String = ItemRow.DEFAULT_DESC_LINES.toString(),
	doubleTapMsText: String = ItemRow.doubleTapMs.toString(),
	wordSplitText: String = ";",
	clever: Boolean = false,
	amountText: String = "10",
	timeText: String = TestEngine.DEFAULT_TIME_SECONDS.toString(),
	searchDiacritics: Boolean = false,
	selectionOrder: List<SelectionAction> = ActionConfig.DEFAULT_ORDER,
	connections: Map<SelectionAction, SelectionAction> = ActionConfig.DEFAULT_CONNECTIONS,
) {
	var parse by mutableStateOf(parse)
		private set
	var defFlip by mutableStateOf(defFlip)
		private set
	var searchDefFlip by mutableStateOf(searchDefFlip)
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
	var searchDiacritics by mutableStateOf(searchDiacritics)
		private set

	/** The ordered selection actions, where the first four hold the strip slots. */
	var selectionOrder by mutableStateOf(selectionOrder)
		private set

	/** The search connections of the connectable actions, Go-to among them. */
	var connections by mutableStateOf(connections)
		private set

	fun toggleParse() {
		parse = !parse
		applyRowOptions()
		AppSettings.set("parseNames", parse)
	}

	fun toggleDefFlip() {
		defFlip = !defFlip
		applyRowOptions()
		AppSettings.set("flipWord", defFlip)
	}

	fun toggleSearchDefFlip() {
		searchDefFlip = !searchDefFlip
		applyRowOptions()
		AppSettings.set("flipSearch", searchDefFlip)
	}

	/** Zero stands for the icon-only display. */
	fun setDescLines(text: String) {
		descLinesText = digitsOnly(text)
		descLinesText.toIntOrNull()?.let {
			applyRowOptions()
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
			applyRowOptions()
			AppSettings.set("doubleTapMs", it)
		}
	}

	fun doubleTapMsFocusLost() {
		if (doubleTapMsText.toIntOrNull() == null)
			doubleTapMsText = ItemRow.doubleTapMs.toString()
	}

	fun toggleClever() {
		clever = !clever
		TestEngine.applyOptions(clever, TestEngine.amount)
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
				TestEngine.applyOptions(TestEngine.isClever, it)
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

	/** The search reads the flag per run, so nothing else needs to react. */
	fun toggleSearchDiacritics() {
		searchDiacritics = !searchDiacritics
		AppSettings.set("searchNoDiacritics", searchDiacritics)
	}

	/** Moves one action in the order, where a connection of a lost slot clears. */
	fun moveSelectionAction(from: Int, to: Int) {
		val moved = ActionConfig(selectionOrder, connections).moved(from, to)
		selectionOrder = moved.order
		connections = moved.connections
		persistSelectionBar()
	}

	/** Connects Go-to to a strip action, null leaves it in the search overflow. */
	fun connectGoto(target: SelectionAction?) {
		// a target off the strip would show nothing, so the door refuses it
		if (target != null && target !in selectionOrder.take(ActionConfig.BAR_SLOTS)) return
		connections = connections.filterKeys { it != SelectionAction.GOTO } +
			(if (target == null) emptyMap() else mapOf(SelectionAction.GOTO to target))
		persistSelectionBar()
	}

	private fun persistSelectionBar() {
		ActionConfig.save(selectionOrder, connections)
	}

	/** The row options move through the one door of ItemRow. */
	private fun applyRowOptions() {
		ItemRow.applyOptions(parse, defFlip, searchDefFlip,
			descLinesText.toIntOrNull() ?: ItemRow.descLines,
			doubleTapMsText.toIntOrNull() ?: ItemRow.doubleTapMs)
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
	private val config = ActionConfig.load()

	val state = SettingsState(
		parse = ItemRow.parse,
		defFlip = ItemRow.defFlip,
		searchDefFlip = ItemRow.searchDefFlip,
		descLinesText = ItemRow.descLines.toString(),
		doubleTapMsText = ItemRow.doubleTapMs.toString(),
		wordSplitText = WordListWriter.wordTranslationSplitter,
		clever = TestEngine.isClever,
		amountText = TestEngine.amount.toString(),
		timeText = TestEngine.getDefaultTime().toString(),
		searchDiacritics = AppSettings.getBool("searchNoDiacritics", false),
		selectionOrder = config.order,
		connections = config.connections,
	)
}
