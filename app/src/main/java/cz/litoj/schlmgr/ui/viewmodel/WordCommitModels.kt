package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.data.db.DbItem

/**
 * The word editor's commit models — the shared contract between the creator
 * popup (the editor) and the view model (the applier), so the screen logic
 * owns its input types instead of reaching into a dialog implementation.
 */

/**
 * One editable translation row of the word editor.
 *
 * @param source the existing translation ([DbItem]) being edited, or `null` for a newly
 * added one
 */
@Stable
class TranslationRow(name: String, description: String, val source: DbItem?) {

	val id: Long = nextId.getAndIncrement()

	var name by mutableStateOf(name)
	var description by mutableStateOf(description)

	private companion object {
		// rows are built on both dispatchers (an edit's io read, the popup's
		// own adds) — the counter must survive the cross-thread increments
		val nextId = java.util.concurrent.atomic.AtomicLong()
	}
}

data class TranslationValue(val name: String, val description: String, val source: DbItem?)

/**
 * Committed state of one OK press — an immutable copy of the edited fields, so
 * the view model applies it without reading the dialog's live state.
 */
data class CreatorResult(
	val name: String,
	val description: String,
	val translations: List<TranslationValue>,
	val removedTranslations: List<DbItem>,
)
