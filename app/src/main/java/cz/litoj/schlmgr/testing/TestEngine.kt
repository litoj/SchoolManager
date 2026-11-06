package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import java.util.Collections

/**
 * All run state lives for one run, and [setTested] prepares it.
 */
class TestEngine {

	private var source: List<TestEntry> = emptyList()
	private var answered: BooleanArray = BooleanArray(0)
	private var groups: List<TestTask> = emptyList()
	private var groupIds = IntArray(0)
	private val usedAnswers = HashMap<Int, HashSet<String>>()
	private var time = 0
	private var doOnSec: Timer? = null
	private var timerThread: Thread? = null

	// the pause flag crosses threads, since the lifecycle writes it from main
	@Volatile
	private var paused = false

	/**
	 * @param amount how many entries to use, -1 or more than [source] uses all of it.
	 *   Clever mode picks by urgency, otherwise randomly.
	 * @param doOnSec tick callback, null for none
	 * @param timeSec seconds one item takes
	 * @param source the candidate entries
	 */
	fun setTested(amount: Int, doOnSec: Timer?, timeSec: Int, source: List<TestEntry>) {
		require(amount == -1 || amount >= 1) { "Amount of tested elements must be >= 1!" }
		require(timeSec >= 1) { "Time given for an item must be >= 1 s!" }
		// an amount that covers the whole source uses all of it,
		// a single-entry source included
		val useAmount = if (amount == -1 || amount >= source.size) source.size else amount
		val selected = if (isClever) cleverTest(source, useAmount) else rndTest(source.toMutableList(), useAmount)
		groups = groupByWholePrompt(selected)
		this.source = groups.flatMap(TestTask::entries)
		// a huge per-item time must not overflow the run into a negative
		time = (timeSec.toLong() * useAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		this.doOnSec = doOnSec
		answered = BooleanArray(this.source.size)
		usedAnswers.clear()
	}

	/**
	 * Merges only entries with exactly the same translation set.
	 * - one batch read supplies every prompt set, so each word needs no separate lookup.
	 * - partial overlap stays separate, since the shown prompts are not the same.
	 */
	private fun groupByWholePrompt(source: List<TestEntry>): List<TestTask> {
		if (source.size < 2 || !ItemRepository.initialized) return source.map { TestTask(listOf(it)) }
		val promptSets = ItemRepository.translationsForWords(source.map { it.word.id })
		val groups = LinkedHashMap<Set<Int>, MutableList<Int>>()
		for ((index, entry) in source.withIndex()) {
			val signature = promptSets[entry.word.id]?.map { it.id }?.toSet() ?: emptySet()
			groups.getOrPut(signature) { mutableListOf() }.add(index)
		}
		groupIds = IntArray(source.size) { -1 }
		return groups.values.mapIndexed { groupId, indices ->
			if (indices.size > 1) for (index in indices) groupIds[index] = groupId
			TestTask(indices.map(source::get))
		}
	}

	/**
	 * Starts the countdown on a worker thread, ticking [doOnSec] each second.
	 * Stop it via [stopTest].
	 */
	fun startTest() {
		timerThread = Thread({
			try {
				val timer = doOnSec
				while (time >= 0) {
					// a pause holds both the tick and the time, so the run continues where it held
					if (!paused && timer != null && !timer.doOnSec(time)) return@Thread
					Thread.sleep(1000)
					if (!paused) time--
				}
			} catch (_: InterruptedException) {
			}
		}, "Test timer").also { it.start() }
	}

	/** Pauses the countdown, so the remaining time holds. */
	fun pauseTest() {
		paused = true
	}

	/** Resumes the countdown where it held. */
	fun resumeTest() {
		paused = false
	}

	/**
	 * Stops the countdown before its natural end, since the run is over.
	 * - see [cz.litoj.schlmgr.ui.activity.TestActivity.reset].
	 * - without it the thread lingers up to one tick past the end.
	 * - it also holds the abandoned activity callback.
	 */
	fun stopTest() {
		timerThread?.interrupt()
	}

	/**
	 * Tests if the user answered correctly, and a null or empty answer always fails.
	 * - a repeated answer inside an ambiguous prompt block fails after its first pass.
	 * - the caller hands the translations of the item, which the run model already holds.
	 * - no per-answer database round trip.
	 *
	 * @return true when the answer matches the tested word in the current direction,
	 *   either the word name or a translation of it when [isReversed] is on.
	 */
	fun isAnswer(index: Int, answer: String?, translations: List<DbItem>): Boolean {
		require(index in 0 until source.size) { "Outside of tested size: $index out of ${source.size - 1}" }
		require(!answered[index]) { "Can't answer more than once, only one attempt allowed!" }
		answered[index] = true
		val entry = source[index]
		val text = answer?.trim()
		var correct = if (requireAllVariants) matchesAllVariants(entry.word, translations, text)
		else matchesAnswer(entry.word, translations, text)
		if (!requireAllVariants && !correct && !isReversed && !text.isNullOrEmpty()) {
			// a synonym must be linked to every shown translation, not just one
			correct = translations.isNotEmpty() && translations.all { translation ->
				ItemRepository.parentIds(translation.id).any { parentId ->
					val parent = ItemRepository.get(parentId)
					parent != null && parent.type == ItemKind.WORD && matchesName(parent.name, text)
				}
			}
		}
		if (!text.isNullOrEmpty()) {
			val group = groupIds.getOrElse(index) { -1 }
			if (group >= 0 && !usedAnswers.getOrPut(group) { HashSet() }.add(text)) {
				// one prompt can only pass once in an ambiguous block
				correct = false
			}
		}
		// the test updates the word and every container holding it.
		// - plain ancestors only, since a reference never counts.
		// - the translations take only the knowledge that the answer demonstrated.
		ItemRepository.bumpWordCounters(entry.word.id, correct)
		bumpTranslations(translations, answer, correct)
		return correct
	}

	/**
	 * Records the answer on the translations of the word.
	 * - on success it records the ones that the answer demonstrated.
	 * - see [creditedTranslations] for that pick.
	 * - on a fail it records all of them, since the answer named no correct translation.
	 */
	private fun bumpTranslations(translations: List<DbItem>, answer: String?, correct: Boolean) {
		if (correct) ItemRepository.bumpCounters(creditedTranslations(translations, answer).map { it.id }, true)
		else ItemRepository.bumpCounters(translations.map { it.id }, false)
	}

	/** The tested entries of the run, as a defensive copy. */
	fun getTestSrc(): List<TestEntry> = ArrayList(source)

	/** The prompt groups of the run, where each group has one shared prompt. */
	fun getTestGroups(): List<TestTask> = groups.toList()

	companion object {
		/** One mutation door, the startup load and the settings screen share it. */
		@JvmStatic
		fun applyOptions(isClever: Boolean, amount: Int) {
			this.isClever = isClever
			this.amount = amount
		}

		/** One mutation door, the startup load and the test setup share it. */
		@JvmStatic
		fun applyReversed(reversed: Boolean) {
			isReversed = reversed
		}

		/** Requires every parsed variant of the answer, rather than any one. */
		@JvmStatic
		fun applyRequireAllVariants(on: Boolean) {
			requireAllVariants = on
		}


		/**
		 * The size of the success-rate belts in percents.
		 * - it drives the computation of how much a word needs to be tested.
		 * - crossing one belt makes a word as urgent as having one test less done.
		 */
		private const val RATIO_BELT = 20

		/**
		 * Ratio belt of words that were never tested, treated as if they always failed.
		 */
		private const val UNTESTED_BELT = 100 / RATIO_BELT

		/**
		 * How much the word needs a retest, the failure rate minus testedness.
		 * - the failure rate counts in [RATIO_BELT]-wide belts.
		 * - tested words slip back, so even first-try-correct words resurface.
		 * - a higher value means a sooner test.
		 */
		@JvmStatic
		fun urgency(word: DbItem): Int {
			val ratio = word.ratio
			return (if (ratio < 0) UNTESTED_BELT else (100 - ratio) / RATIO_BELT) - word.testedCount
		}

		/**
		 * Picks the [amount] most urgent entries.
		 * - ties at the cut share turns via random trim.
		 * - exactly [amount] entries return when amount is no more than the source size.
		 */
		@JvmStatic
		fun cleverTest(source: List<TestEntry>, amount: Int): List<TestEntry> {
			if (source.isEmpty() || amount == 0) return emptyList()
			// a public entry point, so the caller amount is not trusted to fit
			val take = amount.coerceAtMost(source.size)
			val sorted = ArrayList(source)
			sorted.sortWith { a, b -> urgency(b.word).compareTo(urgency(a.word)) }
			val cut = urgency(sorted[take - 1].word)
			var end = take
			while (end < sorted.size && urgency(sorted[end].word) == cut) end++
			return rndTest(ArrayList(sorted.subList(0, end)), take)
		}

		/** Random subset in random order, with [source] trimmed and shuffled in place. */
		@JvmStatic
		fun rndTest(source: MutableList<TestEntry>, amount: Int): MutableList<TestEntry> {
			Collections.shuffle(source)
			// the shuffle made every prefix an equally likely subset
			while (source.size > amount) source.removeAt(source.size - 1)
			return source
		}

		/**
		 * Selected rows into tested entries.
		 * - words directly, chapters expanded, and each word at most one entry.
		 *
		 * @param sources selected rows with container chains, root first
		 * @param children container-children lookup, a fake tree in tests
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

		const val DEFAULT_TIME_SECONDS = 18

		private var defaultTime = DEFAULT_TIME_SECONDS

		@JvmStatic
		fun getDefaultTime(): Int = defaultTime

		/**
		 * @throws IllegalArgumentException when [newTime] is below 1
		 */
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
			private set

		@JvmStatic
		var requireAllVariants = false
			private set

		/**
		 * Whether a test shows the tested word and expects one of its translations,
		 * instead of the default of showing the translations and expecting the word.
		 */
		@JvmStatic
		var isReversed = false
			private set

		/** Item count per test, where -1 or more than the source uses all of it. */
		@JvmStatic
		var amount = 10
			private set

		/**
		 * Whether [answer] names the tested word in the current direction.
		 * - by default that is the word name, and when [isReversed] one of [translations].
		 * - slash and group variants count too.
		 * - null or empty never matches.
		 */
		@JvmStatic
		fun matchesAnswer(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
			if (answer.isNullOrEmpty()) return false
			return if (isReversed) translations.any { matchesName(it.name, answer) }
			else matchesName(word.name, answer)
		}

		/**
		 * Translations that a correct [answer] demonstrated knowledge of.
		 * - when reversed it is the one that the answer named.
		 * - normally it is the whole shown prompt.
		 */
		@JvmStatic
		fun creditedTranslations(translations: List<DbItem>, answer: String?): List<DbItem> =
			if (isReversed) translations.filter { matchesName(it.name, answer ?: "") } else translations

		/**
		 * Whether all parsed variants are answered, separated by a slash.
		 * - each distinct variant must be present, while extra answers are allowed.
		 */
		private fun matchesAllVariants(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
			if (answer.isNullOrBlank()) return false
			val supplied = answer.split('/').map { it.trim() }.toSet()
			val expected = if (isReversed) translations.flatMap { WordNames.parseVariants(it.name) }
			else WordNames.parseVariants(word.name)
			return expected.map { it.trim() }.toSet().all { it in supplied }
		}

		private fun matchesName(name: String, answer: String): Boolean {
			val expected = name.trim()
			val candidate = answer.trim()
			return candidate == expected || WordNames.parseVariants(expected).any { it.trim() == candidate }
		}
	}
}
