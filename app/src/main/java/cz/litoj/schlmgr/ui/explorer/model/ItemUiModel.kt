package cz.litoj.schlmgr.ui.explorer.model

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ratio

/**
 * Immutable, presentation-only description of one explorer list row — the Compose
 * replacement for reading the mutable [ItemRow] inside View adapters.
 *
 * All type knowledge (which drawable belongs to which row kind, whether the row
 * offers a remove button, the success ratio value) is extracted exactly once, in
 * [toUi], so the composables and their hosts stay free of database imports.
 * The [payload] keeps the backing row reachable for host intents (open
 * container, delete, show info, …) without the UI layer touching it.
 *
 * @property key stable identity used as the `LazyColumn` item key: the source
 *   [ItemRow] instance. One row instance always maps to one UI model,
 *   so identity survives recompositions across state updates.
 * @property position 1-based row index, rendered as `n.` in the position cell.
 * @property name the text to display (already parsed / already flipped for a word).
 * @property desc the description/info line attached to the row, `""` when none.
 * @property iconRes drawable for the type icon glued to the end of the position cell.
 * @property ratio success ratio of the item (see `DbItem.ratio`), colors the
 *   position cell background.
 * @property isReference whether the row's link into its container is a
 *   reference link — the item appears here without moving out of its home
 *   (fixed background look).
 * @property isWord whether the row shows a word (hosts use it to decide flip-vs-open).
 * @property flipped word display state: translations shown instead of the name.
 * @property selected whether the row is marked in selection mode.
 * @property payload the backing [ItemRow]; its `path` is only filled for
 *   search-hit rows, where a host may need the hit's container chain.
 * @property trackPath for a search-hit row the hit's container chain
 *   (a defensive copy of [ItemRow.path]); `null` for browse rows.
 * @property translations for a flipped word, one (translation, description) line
 *   per translation — rendered under each translation when inline descriptions are on;
 *   empty for every other row.
 */
data class ItemUiModel(
	val key: Any,
	val position: Int,
	val name: String,
	val desc: String,
	val iconRes: Int,
	val ratio: Int,
	val isReference: Boolean,
	val isWord: Boolean,
	val flipped: Boolean,
	val selected: Boolean,
	val payload: ItemRow,
	val trackPath: List<DbItem>? = null,
	val translations: List<TranslationLine> = emptyList(),
)

/**
 * Which of the two legacy row layouts this row replaces — they differ only in the
 * position cell metrics, so a single [Item] serves both with this switch:
 *
 * - [Browse] = the old `item_hierarchy.xml` (58dp min position cell, 18sp number),
 * - [Search] = the old `item_search.xml` (65dp min position cell, 15sp number).
 */
enum class ExplorerRowVariant { Browse, Search }

/**
 * Stable string keys for `LazyColumn` items. The [ItemUiModel.key] must be
 * Bundle-storable (`LazySaveableStateHolder` requirement) — row objects are
 * not — and stable across recompositions (`ItemUiModel` is rebuilt on every
 * list change, so it can't generate one). The identity-based key of the row
 * instance satisfies both; regenerated only when the identity itself is gone
 * (after a process death the list is rebuilt anyway).
 */
private val keyRegistry = java.util.WeakHashMap<Any, String>()
private val keyCounter = java.util.concurrent.atomic.AtomicLong()

fun Any.stableKey(): String = synchronized(keyRegistry) {
	keyRegistry.getOrPut(this) { "item-${keyCounter.incrementAndGet()}" }
}

/**
 * Maps a row to its immutable Compose counterpart. This is the single place
 * allowed to know how row kinds translate to presentation values.
 */
fun ItemRow.toUi(): ItemUiModel {
	val icon = when {
		isReference -> R.drawable.ic_ref
		isSubject -> R.drawable.ic_subject
		item.type == ItemKind.NOTE -> R.drawable.ic_note
		item.type == ItemKind.CHAPTER -> R.drawable.ic_chapter
		else -> R.drawable.ic_word
	}
	return ItemUiModel(
		key = stableKey(),
		position = position,
		name = toShow,
		desc = info,
		iconRes = icon,
		ratio = item.ratio,
		isReference = isReference,
		isWord = item.type == ItemKind.WORD,
		flipped = flipped,
		selected = selected,
		payload = this,
		trackPath = path,
		translations = translationLines,
	)
}
