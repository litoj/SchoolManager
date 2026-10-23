package cz.litoj.schlmgr.ui.screen.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * Process-wide state of a test being set up.
 * - it holds the picked source chains and the amount, duration and reverse answers.
 * - written only through the doors of this object, so the dedup pass cannot be forgotten.
 * - reset happens only on a genuine fresh entry.
 */
object TestSetupState {

	/**
	 * Set when the setup screen launches the picker, and consumed by its fresh-entry check.
	 * - so a picker return keeps the sources.
	 * - only a genuine fresh entry from the drawer resets the setup.
	 */
	private var openedPicker = false

	/**
	 * Set when a screen prepares the sources straight from a selection.
	 * - the fresh-entry check consumes it, so the filled [list] survives like a picker return.
	 * - the source is the Prepare test action of the subjects screen.
	 */
	private var openedWithSelection = false

	/** Marks the setup as entered from the picker, whose return keeps the sources. */
	fun markOpenedPicker() {
		openedPicker = true
	}

	/** Marks the setup as filled from a selection, so its sources survive the entry. */
	fun markOpenedWithSelection() {
		openedWithSelection = true
	}

	/** Returns the picker mark first, then the selection mark. */
	fun consumeEntryMarks(): Pair<Boolean, Boolean> {
		val marks = openedPicker to openedWithSelection
		openedPicker = false
		openedWithSelection = false
		return marks
	}

	/**
	 * The picked sources, each entry one container chain of an item.
	 * - the root comes first and the picked item last, the TestEntry shape.
	 * - written only by the doors of this object, while readers take [list].
	 * - a snapshot state list, so mutations recompose the screen.
	 */
	private val _list = ArrayList<List<DbItem>>().toMutableStateList()

	@JvmField
	val list: List<List<DbItem>> = _list

	var amountText by mutableStateOf(TestEngine.amount.toString())
		private set

	var timeText by mutableStateOf(TestEngine.getDefaultTime().toString())
		private set

	/**
	 * The engine holds the value, and [cz.litoj.schlmgr.app.Startup] fills it from the setting.
	 */
	var reverseTest by mutableStateOf(TestEngine.isReversed)
		private set

	/** An empty or below-1 field falls back to the engine default at the start. */
	fun setAmount(text: String) {
		amountText = text.filter(Char::isDigit)
	}

	fun setTime(text: String) {
		timeText = text.filter(Char::isDigit)
	}

	fun setReverse(on: Boolean) {
		reverseTest = on
	}

	/** A settings change between visits reaches the next test. */
	fun reset() {
		_list.clear()
		amountText = TestEngine.amount.toString()
		timeText = TestEngine.getDefaultTime().toString()
	}

	/**
	 * A test never mixes subjects, so the mutation doors refuse other-subject rows.
	 * - the subject is that of the first picked chain, null before the first pick.
	 */
	fun pickedSubject(): DbItem? = _list.firstOrNull()?.firstOrNull()

	/**
	 * The chain inputs of one picked row, snapshotted on the caller thread.
	 * - the mutable fields of the rows must not be read on io while main rewrites them.
	 */
	class ChainSeed(val row: ItemRow, val item: DbItem, val path: List<DbItem>?, val parentId: Int?)

	// the path copies, since main rewrites the list of the row in place.
	// the io chain build must not iterate a live one
	fun seedOf(row: ItemRow) = ChainSeed(row, row.item, row.path?.toList(), row.parentId)

	/**
	 * A multi-subject selection is refused whole, and the foreign rows return.
	 * - a picked chapter covers the words in it, so the chains dedup.
	 * - [chainOfSeed] reads the database, so callers stay off main with snapshotted seeds.
	 */
	fun fillFromSelection(seeds: List<ChainSeed>): List<ItemRow> {
		if (seeds.isEmpty()) return emptyList()
		val chains = seeds.map { it.row to chainOfSeed(it) }
		val subject = chains[0].second[0].id
		val foreign = chains.filter { it.second[0].id != subject }.map { it.first }
		if (foreign.isNotEmpty()) return foreign
		_list.clear()
		_list.addAll(chains.map { it.second })
		dedup(_list)
		return emptyList()
	}

	/**
	 * The picker Select: prepends the chains of the selected rows, so new picks lead.
	 * - the pick is whole or nothing, so other-subject rows are refused with nothing added.
	 * - the picker then reports and stays open.
	 * - [chainOfSeed] reads the database, so callers stay off main with snapshotted seeds.
	 */
	fun addPicked(seeds: List<ChainSeed>): List<ItemRow> {
		if (seeds.isEmpty()) return emptyList()
		val chains = seeds.map { it.row to chainOfSeed(it) }
		val subject = pickedSubject()?.id ?: chains[0].second[0].id
		val foreign = chains.filter { it.second[0].id != subject }.map { it.first }
		if (foreign.isNotEmpty()) return foreign
		_list.addAll(0, chains.map { it.second })
		dedup(_list)
		return emptyList()
	}

	fun removeSource(chain: List<DbItem>) {
		_list.remove(chain)
	}

	fun hasSources() = _list.isNotEmpty()

	/** Whether the whole hierarchy is picked, which hides the add-source tile. */
	fun hierarchyPicked() = _list.any { it.size == 1 }

	/**
	 * The root comes first and the picked item last.
	 * - the seed of a search row carries the chain of its hit already.
	 * - the seed of a browse row rebuilds it from the selected-under parent.
	 * - multi-parent items use that parent.
	 */
	fun chainOfSeed(seed: ChainSeed): List<DbItem> {
		seed.path?.let { return it + seed.item }
		val parentId = seed.parentId ?: return listOf(seed.item)
		return ItemRepository.pathTo(parentId) + seed.item
	}

	/**
	 * The dedup pass, where a chain inside another picked chain is covered by it.
	 * - the same chain twice is one source.
	 * - it is internal, since the mutation doors run it and tests exercise it directly.
	 * - any thread works, since the snapshot list recomposes the screen on removal.
	 */
	internal fun dedup(list: MutableList<List<DbItem>>) {
		var pos = 0
		while (pos < list.size - 1) {
			val chain = list[pos]
			var i = list.size - 1
			var removedPos = false
			while (i > pos) {
				val other = list[i]
				if (within(chain, other) && chain.size < other.size) {
					// other sits inside the item of the chain, so the ancestor alone covers it
					list.removeAt(i)
				} else if (within(other, chain)) {
					// the chain sits inside the item of the other, or is the very same chain
					// so keep the other
					list.removeAt(pos)
					removedPos = true
					break
				}
				i--
			}
			if (!removedPos) pos++
		}
	}

	internal fun within(shorter: List<DbItem>, longer: List<DbItem>): Boolean {
		if (shorter.size > longer.size) return false
		for (i in shorter.indices) if (shorter[i].id != longer[i].id) return false
		return true
	}
}

/**
 * The presentation of one picked source chain, the single mapping place.
 * - so the rows of the setup screen stay free of database imports.
 * - the explorer equivalent is [ItemRow.toUi].
 */
data class SourceRowUi(val kind: RowKind, val displayName: String)

fun List<DbItem>.toSourceRowUi() = SourceRowUi(
	kind = when {
		size == 1 -> RowKind.SUBJECT
		last().type == ItemKind.WORD -> RowKind.WORD
		last().type == ItemKind.NOTE -> RowKind.NOTE
		else -> RowKind.CHAPTER
	},
	displayName = ItemRow.nameParser(last().name),
)
