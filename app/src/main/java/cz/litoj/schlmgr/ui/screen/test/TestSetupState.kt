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
 * Process-wide state of a test being set up — picked source chains plus the
 * amount/duration/reverse answers.
 * - written only through this object's doors, so the dedup pass cannot be
 *   forgotten. Reset happens only on a genuine fresh entry.
 */
object TestSetupState {

	/**
	 * Set when the setup screen launches the picker, consumed by its
	 * fresh-entry check — a picker return keeps the sources. Only a
	 * genuine fresh entry (the drawer) resets the setup.
	 */
	@JvmField
	var openedPicker = false

	/**
	 * Set when a screen prepares the sources straight from a selection (the subjects
	 * screen's 'Prepare test') and consumed by the fresh-entry check, so the filled
	 * [list] survives like a picker return.
	 */
	@JvmField
	var openedWithSelection = false

	/**
	 * The picked sources, each entry one item's container chain (root
	 * first, picked item last — the TestEntry shape).
	 * - written only by this object's doors. Readers take [list].
	 * - a snapshot state list, so mutations recompose the screen.
	 */
	private val _list = ArrayList<List<DbItem>>().toMutableStateList()

	@JvmField
	val list: List<List<DbItem>> = _list

	/** Answer to "how many items" — read by the running test when it starts. */
	var amountText by mutableStateOf(TestEngine.amount.toString())
		private set

	/** Answer to "how long per item" — read by the running test when it starts. */
	var timeText by mutableStateOf(TestEngine.getDefaultTime().toString())
		private set

	/**
	 * Whether the test runs in the reverse direction (see [TestEngine.isReversed]).
	 * Restored from the engine, which [cz.litoj.schlmgr.app.Startup] fills from the
	 * persisted setting.
	 */
	var reverseTest by mutableStateOf(TestEngine.isReversed)
		private set

	/**
	 * Sets the tested amount. An empty or below-1 field falls back to
	 * the engine default at the start.
	 */
	fun setAmount(text: String) {
		amountText = text.filter(Char::isDigit)
	}

	/**
	 * Sets the per-item seconds. Digits only, committed as typed — an empty
	 * or below-1 field falls back to the engine default at the start.
	 */
	fun setTime(text: String) {
		timeText = text.filter(Char::isDigit)
	}

	fun setReverse(on: Boolean) {
		reverseTest = on
	}

	/**
	 * The fresh-entry reset — drops the picked sources (the picker owns
	 * its own position) and re-reads the engine's defaults. A settings
	 * change between visits reaches the next test.
	 */
	fun reset() {
		_list.clear()
		amountText = TestEngine.amount.toString()
		timeText = TestEngine.getDefaultTime().toString()
	}

	/**
	 * The one subject every source must belong to — the first picked chain's
	 * subject, `null` before the first pick.
	 * - a test never mixes subjects — the mutation doors refuse other-subject
	 *   rows.
	 */
	fun pickedSubject(): DbItem? = _list.firstOrNull()?.firstOrNull()

	/**
	 * One picked row's chain inputs, snapshotted on the caller's thread —
	 * the rows' mutable fields must not be read on io against the main
	 * thread rewriting them.
	 */
	class ChainSeed(val row: ItemRow, val item: DbItem, val path: List<DbItem>?, val parentId: Int?)

	// the path copies — main rewrites the row's list in place, and the io
	// chain build must not iterate a live one
	fun seedOf(row: ItemRow) = ChainSeed(row, row.item, row.path?.toList(), row.parentId)

	/**
	 * 'Prepare test': replace the sources with the selected rows' chains.
	 * - deduped (a picked chapter covers the words in it).
	 * - a multi-subject selection is refused whole (foreign rows return, the
	 *   list stays).
	 * - [chainOfSeed] reads the database — callers stay off main, handing
	 *   snapshotted seeds.
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
	 * The picker's Select — prepends the selected rows' chains (new picks lead)
	 * and dedup.
	 * - the pick is whole or nothing — other-subject rows are refused with
	 *   nothing added, so the picker reports and stays open.
	 * - [chainOfSeed] reads the database — callers stay off main with
	 *   snapshotted seeds.
	 */
	fun addPicked(seeds: List<ChainSeed>): List<ItemRow> {
		if (seeds.isEmpty()) return emptyList()
		val chains = seeds.map { it.row to chainOfSeed(it) }
		val subject = pickedSubject()?.id ?: chains[0].second[0].id
		val foreign = chains.filter { it.second[0].id != subject }.map { it.first }
		if (foreign.isNotEmpty()) return foreign
		// the whole pick leads the list, in its selection order
		_list.addAll(0, chains.map { it.second })
		dedup(_list)
		return emptyList()
	}

	fun removeSource(chain: List<DbItem>) {
		_list.remove(chain)
	}

	fun hasSources() = _list.isNotEmpty()

	/** Whether the whole hierarchy is picked (the add-source tile hides then). */
	fun hierarchyPicked() = _list.any { it.size == 1 }

	/**
	 * One seed's source chain — root first, picked item last.
	 * - a search row's seed carries its hit's chain already — a browse row's
	 *   seed rebuilds from the selected-under parent (multi-parent items
	 *   use it).
	 */
	fun chainOfSeed(seed: ChainSeed): List<DbItem> {
		seed.path?.let { return it + seed.item }
		val parentId = seed.parentId ?: return listOf(seed.item)
		return ItemRepository.pathTo(parentId) + seed.item
	}

	/**
	 * The dedup pass — a chain inside another picked chain's item is covered by
	 * it, and the same chain twice is one source.
	 * - internal — the mutation doors run it, and tests exercise it directly.
	 * - any thread — the snapshot list recomposes the screen on removal.
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
					// `other` sits inside chain's item — the ancestor alone covers it
					list.removeAt(i)
				} else if (within(other, chain)) {
					// chain sits inside `other`'s item, or is the very same chain — keep the other
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
 * Presentation of one picked source chain — the single mapping place, so the
 * setup screen's rows stay free of database imports (the explorer equivalent
 * is [ItemRow.toUi]).
 */
data class SourceRowUi(val kind: RowKind, val displayName: String)

/**
 * Maps one chain — length 1 is a whole subject, the last item's type
 * otherwise. The name is the parsed last item's name.
 */
fun List<DbItem>.toSourceRowUi() = SourceRowUi(
	kind = when {
		size == 1 -> RowKind.SUBJECT
		last().type == ItemKind.WORD -> RowKind.WORD
		last().type == ItemKind.NOTE -> RowKind.NOTE
		else -> RowKind.CHAPTER
	},
	displayName = ItemRow.nameParser(last().name),
)
