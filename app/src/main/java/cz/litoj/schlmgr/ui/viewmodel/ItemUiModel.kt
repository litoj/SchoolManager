package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ratio

/**
 * One explorer list row for Compose — presentation only, built in [toUi]
 * (test results build it directly).
 *
 * @property key stable `LazyColumn` key ([stableKey] string, the item itself in test results)
 * @property position 1-based index, or the tested count in test results
 * @property name parsed display name (a flipped word's translations)
 * @property description line under the name, empty when none
 * @property payload the backing [ItemRow], or `null`
 * @property trackPath a hit's container chain, null otherwise
 */
data class ItemUiModel(
	val key: Any,
	val position: Int,
	val name: String,
	val description: String,
	val kind: RowKind,
	val ratio: Int,
	val isReference: Boolean,
	val hasSource: Boolean = false,
	val selected: Boolean,
	val payload: ItemRow? = null,
	val trackPath: List<DbItem>? = null,
	val translations: List<TranslationLine> = emptyList(),
)

enum class ExplorerRowVariant { Browse, Search }

/**
 * Stable string keys for `LazyColumn` items.
	 * - [ItemUiModel.key] must be Bundle-storable (row objects are not) and stable across recompositions (`ItemUiModel` rebuilds on every change).
 */
private val keyRegistry = java.util.WeakHashMap<Any, String>()
private val keyCounter = java.util.concurrent.atomic.AtomicLong()

fun Any.stableKey(): String = synchronized(keyRegistry) {
	keyRegistry.getOrPut(this) { "item-${keyCounter.incrementAndGet()}" }
}

/**
 * Which kind of thing one row shows. The semantic identity the UI maps to
 * its type icon — the state layer never resolves drawables (see `RowKind.iconRes`
 * in the component layer).
 */
enum class RowKind { SUBJECT, NOTE, CHAPTER, WORD }

fun ItemRow.toUi(): ItemUiModel {
	val kind = when {
		isSubject -> RowKind.SUBJECT
		item.type == ItemKind.NOTE -> RowKind.NOTE
		item.type == ItemKind.CHAPTER -> RowKind.CHAPTER
		else -> RowKind.WORD
	}
	return ItemUiModel(
		key = stableKey(),
		position = position,
		name = toShow,
		description = info,
		kind = kind,
		ratio = item.ratio,
		isReference = isReference,
		hasSource = hasSource,
		selected = selected,
		payload = this,
		trackPath = path?.toList(),
		translations = translationLines,
	)
}
