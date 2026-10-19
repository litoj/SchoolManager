package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import java.util.Collections

/**
 * Creates and manages a word test. All run state (source, answered flags,
 * timer) lives for one run — use [setTested] to prepare it.
 */
class TestEngine {

	private var source: List<TestEntry> = emptyList()
	private var answered: BooleanArray = BooleanArray(0)
	private var time = 0
	private var doOnSec: Timer? = null
	private var timerThread: Thread? = null

	/**
	 * Prepares everything for the next test.
	 *
	 * @param amount how many entries to use — -1 or more than [source] uses all of it.
	 *   Clever mode picks by urgency, otherwise randomly.
	 * @param doOnSec tick callback, null for none
	 * @param timeSec seconds one item takes
	 * @param source the candidate entries
	 */
	fun setTested(amount: Int, doOnSec: Timer?, timeSec: Int, source: List<TestEntry>) {
		// -1 uses the whole source — anything below 1 is invalid
		require(amount == -1 || amount >= 1) { "Amount of tested elements must be >= 1!" }
		require(timeSec >= 1) { "Time given for an item must be >= 1 s!" }
		// an amount that covers the whole source uses all of it (a
		// single-entry source included)
		val useAmount = if (amount == -1 || amount >= source.size) source.size else amount
		this.source = if (isClever) cleverTest(source, useAmount) else rndTest(source.toMutableList(), useAmount)
		// a huge per-item time must not overflow the run into a negative
		time = (timeSec.toLong() * useAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		this.doOnSec = doOnSec
		answered = BooleanArray(this.source.size)
	}

	/** Starts the countdown on a worker thread, ticking [doOnSec] each second — stop via [stopTest]. */
	fun startTest() {
		timerThread = Thread({
			try {
				val timer = doOnSec
				while (time >= 0) {
					if (timer != null && !timer.doOnSec(time)) return@Thread
					Thread.sleep(1000)
					time--
				}
			} catch (_: InterruptedException) {
			}
		}, "Test timer").also { it.start() }
	}

	/**
	 * Stops the countdown before its natural end — the run is over (see
	 * [cz.litoj.schlmgr.ui.activity.TestActivity.reset]).
	 * - without it the thread lingers up to one tick past the end.
	 * - it holds the abandoned activity's callback.
	 */
	fun stopTest() {
		timerThread?.interrupt()
	}

	/**
	 * Tests if the user answered correctly — a null or empty answer always fails.
	 * - the caller hands the item's translations — the run's model already holds them.
	 * - no per-answer database round trip.
	 *
	 * @return `true` when the answer matches the tested word in the current
	 * direction — the word's name (or a word linked to its translations) by
	 * default, one of its translations when [isReversed] is on.
	 */
	fun isAnswer(index: Int, answer: String?, translations: List<DbItem>): Boolean {
		require(index in 0 until source.size) { "Outside of tested size: $index out of ${source.size - 1}" }
		require(!answered[index]) { "Can't answer more than once, only one attempt allowed!" }
		answered[index] = true
		val entry = source[index]
		var correct = matchesAnswer(entry.word, translations, answer)
		if (!correct && !isReversed && !answer.isNullOrEmpty()) {
			// the answer may name any word linked to every translation of
			// the tested word (the reverse direction of the word→translation links)
			for (translation in translations) {
				correct = false
				for (parentId in ItemRepository.parentIds(translation.id)) {
					val parent = ItemRepository.get(parentId) ?: continue
					if (parent.type != ItemKind.WORD) continue
					if (WordNames.parseVariants(parent.name).any { it == answer }) {
						correct = true
						break
					}
				}
				if (!correct) break
			}
		}
		// the test updates the word and every container holding it — plain
		// ancestors only, a reference never counts — and the translations
		// only the knowledge the answer demonstrated
		ItemRepository.bumpWordCounters(entry.word.id, correct)
		bumpTranslations(translations, answer, correct)
		return correct
	}

	/**
	 * Records the answer on the word's translations — the ones the answer
	 * demonstrated knowledge of on success (see [creditedTranslations]), and
	 * all of them on a fail (the answer then named no correct translation).
	 */
	private fun bumpTranslations(translations: List<DbItem>, answer: String?, correct: Boolean) {
		if (correct) ItemRepository.bumpCounters(creditedTranslations(translations, answer).map { it.id }, true)
		else ItemRepository.bumpCounters(translations.map { it.id }, false)
	}

	/** The run's tested entries, as a defensive copy. */
	fun getTestSrc(): List<TestEntry> = ArrayList(source)

	companion object {

		/**
		 * Size of the success-rate belts (in percents) used to compute how much a word
		 * needs to be tested. Crossing one belt makes a word as urgent as having one
		 * test less done.
		 */
		private const val RATIO_BELT = 20

		/**
		 * Ratio belt of words that were never tested — treated as if they always failed.
		 */
		private const val UNTESTED_BELT = 100 / RATIO_BELT

		/**
		 * How much the word needs a retest — failure rate (in [RATIO_BELT]-wide
		 * belts) minus testedness.
		 * - tested words slip back, so even first-try-correct words resurface.
		 * - higher = test sooner.
		 */
		@JvmStatic
		fun urgency(word: DbItem): Int {
			val ratio = word.ratio
			return (if (ratio < 0) UNTESTED_BELT else (100 - ratio) / RATIO_BELT) - word.testedCount
		}

		/**
		 * Picks the [amount] most urgent entries. Ties at the cut share turns via
		 * random trim, and exactly [amount] entries are returned when
		 * `amount <= source.size`.
		 */
		@JvmStatic
		fun cleverTest(source: List<TestEntry>, amount: Int): List<TestEntry> {
			// an empty source (or nothing picked) selects nothing
			if (source.isEmpty() || amount == 0) return emptyList()
			// a public entry point — the caller's amount is not trusted to fit
			val take = amount.coerceAtMost(source.size)
			val sorted = ArrayList(source)
			sorted.sortWith { a, b -> urgency(b.word).compareTo(urgency(a.word)) }
			val cut = urgency(sorted[take - 1].word)
			var end = take
			while (end < sorted.size && urgency(sorted[end].word) == cut) end++
			return rndTest(ArrayList(sorted.subList(0, end)), take)
		}

		/** Random subset in random order — [source] is trimmed and shuffled in place. */
		@JvmStatic
		fun rndTest(source: MutableList<TestEntry>, amount: Int): MutableList<TestEntry> {
			Collections.shuffle(source)
			// the shuffle made every prefix an equally likely subset
			while (source.size > amount) source.removeAt(source.size - 1)
			return source
		}

		/**
		 * Selected rows into tested entries.
		 * - words directly, chapters expanded — each word at most one entry.
		 *
		 * @param sources selected rows with container chains (root first)
		 * @param children container-children lookup (a fake tree in tests)
		 */
		@JvmStatic
		@JvmOverloads
		fun convertAll(
			sources: List<List<DbItem>>,
			children: (Int) -> List<DbItem> = ItemRepository::children,
		): List<TestEntry> {
			val entries = ArrayList<TestEntry>()
			val visited = HashSet<Int>()
			for (path in sources) {
				val last = path.last()
				if (last.type == ItemKind.WORD) {
					if (visited.add(last.id)) entries.add(TestEntry(last, path))
				} else if (last.type == ItemKind.CHAPTER) convertContent(path, children, visited, entries)
			}
			return entries
		}

		private fun convertContent(
			path: List<DbItem>,
			children: (Int) -> List<DbItem>,
			visited: MutableSet<Int>,
			out: MutableList<TestEntry>,
		) {
			for (child in children(path.last().id)) {
				if (!visited.add(child.id)) continue
				when (child.type) {
					ItemKind.WORD -> out.add(TestEntry(child, path + child))
					ItemKind.CHAPTER -> convertContent(path + child, children, visited, out)
					else -> {}
				}
			}
		}

		/** The default seconds per item a fresh program state carries. */
		const val DEFAULT_TIME_SECONDS = 18

		private var defaultTime = DEFAULT_TIME_SECONDS

		@JvmStatic
		fun getDefaultTime(): Int = defaultTime

		/** Sets the default seconds per item — [newTime] must be >= 1. */
		@JvmStatic
		fun setDefaultTime(newTime: Int) {
			require(newTime >= 1) { "Duration of a test must be >= 1 s!" }
			defaultTime = newTime
		}

		/**
		 * Whether the created tests will prefer less tested and worse result
		 * words when selecting the content of a test.
		 */
		@JvmStatic
		var isClever = true

		/**
		 * Whether a test shows the tested word and expects one of its translations
		 * instead of showing the translations and expecting the word (the default).
		 */
		@JvmStatic
		var isReversed = false

		/** Item count per test — -1 or more than the source uses all of it. */
		@JvmStatic
		var amount = 10

		/**
		 * Whether [answer] names the tested word in the current direction (word name
		 * normally, one of [translations] when [isReversed]). `/()` variants count
		 * too, and null or empty never matches.
		 */
		@JvmStatic
		fun matchesAnswer(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
			if (answer.isNullOrEmpty()) return false
			return if (isReversed) translations.any { matchesName(it.name, answer) }
			else matchesName(word.name, answer)
		}

		/**
		 * Translations a correct [answer] demonstrated knowledge of — the one it
		 * named when reversed, the whole shown prompt normally.
		 */
		@JvmStatic
		fun creditedTranslations(translations: List<DbItem>, answer: String?): List<DbItem> =
			if (isReversed) translations.filter { matchesName(it.name, answer ?: "") } else translations

		/** Whether the answer equals the name or one of its `/()` variants. */
		private fun matchesName(name: String, answer: String): Boolean =
			answer == name || WordNames.parseVariants(name).any { it == answer }
	}
}
