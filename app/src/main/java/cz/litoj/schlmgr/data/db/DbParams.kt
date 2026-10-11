package cz.litoj.schlmgr.data.db

/** A `null` counter pair inherits the word's counters — only set them explicitly when they differ. */
internal class NewTranslation(
	val name: String,
	val description: String?,
	val passedTests: Int? = null,
	val failedTests: Int? = null,
)

internal data class InsertWordParams(
	val parentId: Int,
	val name: String,
	val description: String?,
	val translations: List<NewTranslation>,
	val passedTests: Int = 0,
	val failedTests: Int = 0,
)

internal data class AddTranslationsParams(
	val scope: Int,
	val wordId: Int,
	val translations: List<NewTranslation>,
)

internal data class MoveRequest(
	val childId: Int,
	val oldParentId: Int,
	val newParentId: Int,
	val position: Int?,
)

data class ShiftRange(
	val parentId: Int,
	val delta: Int,
	val from: Int,
	val to: Int,
)

data class KeptTranslation(
	val id: Int,
	val name: String,
	val description: String,
)

/**
 * Committed word edit.
 * - kept rows, removed rows, new translations, plus references that follow fork.
 * - fork decision lives with repository.
 */
data class WordEdit(
	val wordId: Int,
	val parentId: Int,
	val name: String,
	val description: String?,
	val kept: List<KeptTranslation>,
	val removed: List<Int>,
	val additions: List<Pair<String, String?>>,
	val referencesToFork: List<Int> = emptyList(),
)
