package cz.litoj.schlmgr.ui.screen.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.toMutableStateList
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.ui.component.NumberInput
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * One setup session, whose lifecycle belongs to the shell navigation owner.
 * - [TestSetupState.shared] is the compatibility instance for callers outside it.
 * - it holds the picked source chains and the amount, duration, reverse, content and rate answers.
 * - the sources stay until the user clears them, since testers gathered one test across chapters.
 * - the answers reset only on a genuine fresh entry.
 */
class TestSetupState {

	companion object {
		val shared = TestSetupState()
	}

	/**
	 * Set when the setup screen launches the picker, and consumed by its fresh-entry check.
	 * - so a picker return keeps the typed answers.
	 * - only a genuine fresh entry from the drawer resets them.
	 */
	private var openedPicker = false

	/**
	 * Set when a screen adds sources straight from a selection.
	 * - the fresh-entry check consumes it, so the answers survive like a picker return.
	 * - the source is the Add to test action of the subjects screen.
	 */
	private var openedWithSelection = false

	/** Marks the setup as entered from the picker, whose return keeps the answers. */
	fun markOpenedPicker() {
		openedPicker = true
	}

	/** Marks the setup as fed from a selection, so its answers survive the entry. */
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

	/** The deduped word count of all sources, which the amount caption shows. */
	var poolCount by mutableIntStateOf(0)
		private set

	/** The word count of one source chain, which its row shows. */
	private val _sourceCounts = mutableStateMapOf<List<DbItem>, Int>()
	val sourceCounts: Map<List<DbItem>, Int> = _sourceCounts

	/** Source multiplicity per word, so removal never walks another source. */
	private val sourceWordIds = mutableMapOf<List<DbItem>, Set<Int>>()
	private val wordSourceCounts = mutableMapOf<Int, Int>()

	/** The deduped pool words, which the total-time caption times out. */
	private var poolEntries by mutableStateOf<List<TestEntry>>(emptyList())

	var amountText by mutableStateOf(TestEngine.amount.toString())
		private set

	var timeText by mutableStateOf(TestEngine.getSecondsPerChar().toString())
		private set

	/**
	 * The engine holds the value, and [cz.litoj.schlmgr.app.Startup] fills it from the setting.
	 */
	var reverseTest by mutableStateOf(TestEngine.isReversed)
		private set

	/**
	 * The last pick rides on the engine, so the next setup starts where this one ended.
	 * - on means urgency ranking, off means a random pool.
	 */
	var cleverTest by mutableStateOf(TestEngine.isClever)
		private set

	/**
	 * The typed rate limit, while the engine keeps the last usable one.
	 * - a fresh setup opens it at 100, a forgotten low limit would hide words without a trace.
	 */
	var maxRateText by mutableStateOf(TestEngine.maxRate.toString())
		private set

	/** An empty or below-1 field falls back to the engine default at the start. */
	fun setAmount(text: String) {
		amountText = text.filter(Char::isDigit)
	}

	/** An empty or unusable field falls back to the engine setting at the start. */
	fun setTime(text: String) {
		timeText = NumberInput.decimal(text)
	}

	fun setReverse(on: Boolean) {
		reverseTest = on
	}

	fun setClever(on: Boolean) {
		cleverTest = on
	}

	fun setMaxRate(text: String) {
		maxRateText = text.filter(Char::isDigit)
	}

	/** The whole setup emptied, the sources with the answers. */
	@Synchronized
	fun reset() {
		clearSources()
		sourceEntries.clear()
		resetAnswers()
	}

	/** The engine holds the last picks, so a fresh setup returns to them and keeps the sources. */
	@Synchronized
	fun resetAnswers() {
		amountText = TestEngine.amount.toString()
		timeText = TestEngine.getSecondsPerChar().toString()
		reverseTest = TestEngine.isReversed
		cleverTest = TestEngine.isClever
		TestEngine.applyMaxRate(100)
		maxRateText = TestEngine.maxRate.toString()
	}

	/** The Clear list action, which drops every source with its counts and pool. */
	@Synchronized
	fun clearSources() {
		_list.clear()
		poolCount = 0
		poolEntries = emptyList()
		_sourceCounts.clear()
		sourceWordIds.clear()
		wordSourceCounts.clear()
	}

	/**
	 * The counts of the setup captions, from the captured source entries.
	 * - the pool dedups, the single source counts may overlap.
	 * - the caller keeps it off main, like the mutation doors.
	 */
	@Synchronized
	fun refreshCounts() {
		_sourceCounts.clear()
		sourceWordIds.clear()
		wordSourceCounts.clear()
		val seen = HashSet<Int>()
		val pool = ArrayList<TestEntry>()
		_list.forEach { chain ->
			// the cached entries ignore the limit, so the count follows each limit change
			val entries = (sourceEntries[chain]
				?: childrenLookup?.let { lookup -> TestEngine.convertAll(listOf(chain), children = lookup) }.orEmpty())
				.filter { TestEngine.withinMaxRate(it.word) }
			val ids = entries.mapTo(HashSet()) { it.word.id }
			sourceWordIds[chain] = ids
			_sourceCounts[chain] = ids.size
			ids.forEach { id -> wordSourceCounts[id] = (wordSourceCounts[id] ?: 0) + 1 }
			entries.forEach { if (seen.add(it.word.id)) pool.add(it) }
		}
		poolEntries = pool
		poolCount = wordSourceCounts.size
	}

	/** A stable copy for test conversion while source mutations run on IO. */
	@Synchronized
	fun sourceSnapshot(): List<List<DbItem>> = _list.toList()

	/** Entries stay available to an outstanding Undo even after Clear empties the visible sources. */
	private val sourceEntries = mutableMapOf<List<DbItem>, List<TestEntry>>()

	// the unit tests replace it with a fake tree, production doors supply captured entries instead
	internal var childrenLookup: ((Int) -> List<DbItem>)? = null

	/**
	 * The seconds of the total-time caption, where an empty field takes the setting.
	 * - the count clamps to the pool, since the run asks no more than that.
	 */
	fun timeSeconds(): Int {
		val perChar = NumberInput.amount(timeText) ?: TestEngine.getSecondsPerChar()
		val amount = (amountText.toIntOrNull() ?: 0).takeIf { it >= 1 } ?: TestEngine.amount
		val count = if (poolCount > 0) minOf(amount, poolCount) else amount
		return TestEngine.testTime(perChar, poolEntries.take(count))
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
	 * The picker Select and the Add to test action: prepends the chains of the rows, so new picks lead.
	 * - the pick is whole or nothing, so other-subject rows are refused with nothing added.
	 * - a picked chapter covers the words in it, so the chains dedup.
	 * - callers supply captured parent paths and entries, so this mutation performs no reads.
	 */
	@Synchronized
	fun addPicked(
		seeds: List<ChainSeed>, parentPaths: Map<Int, List<DbItem>> = emptyMap(),
		entries: Map<List<DbItem>, List<TestEntry>> = emptyMap(),
	): List<ItemRow> {
		if (seeds.isEmpty()) return emptyList()
		val chains = seeds.map { it.row to chainOfSeed(it, parentPaths) }
		val subject = pickedSubject()?.id ?: chains[0].second[0].id
		val foreign = chains.filter { it.second[0].id != subject }.map { it.first }
		if (foreign.isNotEmpty()) return foreign
		_list.addAll(0, chains.map { it.second })
		dedup(_list)
		sourceEntries.putAll(entries)
		refreshCounts()
		return emptyList()
	}

	/** Returns the index for [restoreSource], null when a repeated tap finds the chain gone. */
	@Synchronized
	fun removeSource(chain: List<DbItem>): Int? {
		val index = _list.indexOf(chain)
		if (index < 0) return null
		_list.removeAt(index)
		_sourceCounts.remove(chain)
		sourceWordIds.remove(chain).orEmpty().forEach { id ->
			val remaining = wordSourceCounts.getValue(id) - 1
			if (remaining == 0) wordSourceCounts.remove(id)
			else wordSourceCounts[id] = remaining
		}
		poolCount = wordSourceCounts.size
		// the pool comes off the cached maps too, since removal never reads the database
		poolEntries = poolEntries.filter { it.word.id in wordSourceCounts }
		return index
	}

	/**
	 * The undo of [removeSource], returning false when the chain is refused.
	 * - the list can change in between, so the one subject rule and the dedup still apply.
	 * - the list can also shrink, so the index caps at its end.
	 */
	@Synchronized
	fun restoreSource(chain: List<DbItem>, index: Int): Boolean {
		if (chain !in sourceEntries && childrenLookup == null) return false
		val subject = pickedSubject()
		if (subject != null && subject.id != chain[0].id) return false
		_list.add(index.coerceAtMost(_list.size), chain)
		dedup(_list)
		refreshCounts()
		return true
	}

	fun hasSources() = _list.isNotEmpty()

	/** Whether the whole hierarchy is picked, which mutes the add-source tile. */
	fun hierarchyPicked() = _list.any { it.size == 1 }

	/**
	 * The root comes first and the picked item last.
	 * - the seed of a search row carries the chain of its hit already.
	 * - the seed of a browse row takes its selected-under parent from [parentPaths].
	 * - multi-parent items use that parent.
	 */
	fun chainOfSeed(seed: ChainSeed, parentPaths: Map<Int, List<DbItem>> = emptyMap()): List<DbItem> {
		seed.path?.let { return it + seed.item }
		val parentId = seed.parentId ?: return listOf(seed.item)
		return parentPaths.getValue(parentId) + seed.item
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

internal class TestSetupOwner : ViewModel() {
	val state = TestSetupState()
}

/**
 * The presentation of one picked source chain, the single mapping place.
 * - so the rows of the setup screen stay free of database imports.
 * - the explorer equivalent is [ItemRow.toUi].
 */
data class SourceRowUi(val kind: RowKind, val displayName: String, val words: Int)

fun List<DbItem>.toSourceRowUi(setup: TestSetupState = TestSetupState.shared) = SourceRowUi(
	kind = when {
		size == 1 -> RowKind.SUBJECT
		last().type == ItemKind.WORD -> RowKind.WORD
		last().type == ItemKind.NOTE -> RowKind.NOTE
		else -> RowKind.CHAPTER
	},
	displayName = ItemRow.nameParser(last().name),
	words = setup.sourceCounts[this] ?: 0,
)
