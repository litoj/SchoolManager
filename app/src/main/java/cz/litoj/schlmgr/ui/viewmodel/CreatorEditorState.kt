package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.DbItem

/** Field state and editing rules of one creator editor instance. */
@Stable
class CreatorEditorState(
	edited: ItemRow?,
	rows: List<TranslationRow>,
	private val wordContent: Boolean,
	private val noteContent: Boolean,
) {
	var focusNameOnOpen by mutableStateOf(true)
	var name by mutableStateOf(edited?.item?.name ?: "")
	var description by mutableStateOf(edited?.item?.description ?: "")
	var showRequired by mutableStateOf(false)

	val translations: SnapshotStateList<TranslationRow> =
		rows.toMutableStateList()
			.apply { if (isEmpty()) add(TranslationRow("", "", null)) }

	val removedTranslations = mutableListOf<DbItem>()

	private val initialName = name
	private val initialDescription = description
	private val initialRows = translations.map { it.name to it.description }

	val isDirty: Boolean
		get() = name != initialName || description != initialDescription ||
			removedTranslations.isNotEmpty() ||
			translations.map { it.name to it.description } != initialRows

	val nameFieldFocus = mutableStateMapOf<Long, FocusRequester>()
	var pendingFocusId by mutableStateOf<Long?>(null)
	var pendingKeyboardShow by mutableStateOf(false)

	val itemNameMissing: Boolean
		get() = if (wordContent) WordNames.splitEscaped(name).any { it.isBlank() } else name.isBlank()

	val descriptionMissing: Boolean get() = noteContent && description.isBlank()

	val canSubmit: Boolean
		get() = !itemNameMissing && !descriptionMissing &&
			(!wordContent || translations.any { it.name.isNotBlank() })

	fun result() = CreatorResult(
		name = name,
		description = description,
		translations = translations.map { TranslationValue(it.name, it.description, it.source) },
		removedTranslations = removedTranslations.toList(),
	)

	fun discardBlankTranslations() {
		val blanks = translations.filter { it.name.isBlank() }
		blanks.forEach {
			it.source?.let(removedTranslations::add)
			nameFieldFocus.remove(it.id)
		}
		translations.removeAll(blanks.toSet())
	}

	fun swapWithTranslations() {
		discardBlankTranslations()
		val nextName = translations.joinToString("\\") { it.name }
		val nextDescription = translations.joinToString("\\") { it.description }
		val rowDescriptions = WordNames.splitEscaped(description)
		translations.clear()
		WordNames.splitEscaped(name).forEachIndexed { index, word ->
			translations.add(TranslationRow(word, rowDescriptions.getOrNull(index) ?: "", null))
		}
		name = nextName
		description = nextDescription
		nameFieldFocus.clear()
		pendingFocusId = null
	}
}
