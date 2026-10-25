package cz.litoj.schlmgr.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.data.db.DbItem

/**
 * The commit models of the word editor, shared by the creator popup and the view model.
 * - the popup is the editor and the view model is the applier.
 * - so the screen logic owns its input types instead of reaching into a dialog implementation.
 */

/**
 * @param source the existing translation, a [DbItem], being edited, or null for a new one
 */
@Stable
class TranslationRow(name: String, description: String, val source: DbItem?) {

	val id: Long = nextId.getAndIncrement()

	var name by mutableStateOf(name)
	var description by mutableStateOf(description)

	private companion object {
		// rows are built on both dispatchers: an edit reads on io, and the popup adds.
		// so the counter must survive the cross-thread increments
		val nextId = java.util.concurrent.atomic.AtomicLong()
	}
}

data class TranslationValue(val name: String, val description: String, val source: DbItem?)

/**
 * The immutable copy of the edited fields, so the view model never reads the live dialog state.
 */
data class CreatorResult(
	val name: String,
	val description: String,
	val translations: List<TranslationValue>,
	val removedTranslations: List<DbItem>,
)
