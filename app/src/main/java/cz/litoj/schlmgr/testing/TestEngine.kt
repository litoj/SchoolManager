package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.dao.WordDao
import cz.litoj.schlmgr.db.ratio
import cz.litoj.schlmgr.db.sfCount
import java.util.Collections
import java.util.Random

/**
 * Creates and manages a word test — the port of the engine's `Test` class onto
 * database rows. All state (source, answered flags, timer) lives for one run;
 * use [setTested] to prepare a run from a source list.
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
	 * @param amount  the amount of objects to be randomly picked for the test or
	 *                -1 if all of the words are supposed to be used
	 * @param doOnSec action to be done every second of the test, last call on time out
	 * @param timeSec time given per item in test
	 * @param source  list of paths to the tested objects. Don't include the subject root.
	 */
	fun setTested(amount: Int, doOnSec: Timer?, timeSec: Int, source: List<TestEntry>) {
		// the legacy else-if chain: only an explicit amount validates the rest
		val useAmount = if (amount == -1 || amount > source.size) source.size
		else {
			require(amount >= 1) { "Amount of tested elements must be >= 1!" }
			require(timeSec >= 1) { "Time given for an item must be >= 1 s!" }
			require(source.size >= 2) { "The source' size must be > 1!" }
			amount
		}
		this.source = if (isClever) cleverTest(source, useAmount) else rndTest(source.toMutableList(), useAmount)
		time = timeSec * useAmount
		this.doOnSec = doOnSec
		answered = BooleanArray(this.source.size)
	}

	/** Starts the test. */
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
	 * [cz.litoj.schlmgr.ui.activity.TestActivity.reset]). Without it the
	 * thread lingers for up to one tick past the end, still holding the
	 * abandoned activity's callback.
	 */
	fun stopTest() {
		timerThread?.interrupt()
	}

	/**
	 * Tests if the user answered correctly.
	 *
	 * @param index  index of the answered object
	 * @param answer the answer of the user
	 * @return `true` if the answer matches the tested word in the current
	 * direction — the word's name (or a word linked to its translations) by
	 * default, one of its translations when [isReversed] is on — otherwise
	 * `false`
	 */
	fun isAnswer(index: Int, answer: String?): Boolean {
		require(index < source.size) { "Outside of tested size: $index out of ${source.size - 1}" }
		require(!answered[index]) { "Can't answer more than once, only one attempt allowed!" }
		answered[index] = true
		val entry = source[index]
		val translations = entry.translations()
		var correct = matchesAnswer(entry.word, translations, answer)
		if (!correct && !isReversed && !answer.isNullOrEmpty()) {
			// the answer may name any word linked to every translation of
			// the tested word (the reverse direction of the word→translation links)
			for (trl in translations) {
				correct = false
				for (parentId in ItemRepository.parentIds(trl.id)) {
					val parent = ItemRepository.get(parentId) ?: continue
					if (parent.type != ItemKind.WORD) continue
					if (WordDao.parseVariants(parent.name).any { it == answer }) {
						correct = true
						break
					}
				}
				if (!correct) break
			}
		}
		// the test updates the whole hierarchy with the result: the chapter
		// path (the word included) gets the plain result, the translations
		// only the knowledge the answer demonstrated
		ItemRepository.bumpSf(entry.path.map { it.id }, correct)
		bumpTranslations(translations, answer, correct)
		return correct
	}

	/**
	 * Records the answer on the word's translations: the ones the answer
	 * demonstrated knowledge of on success (see [creditedTranslations]), all of
	 * them on a fail — the answer then named no correct translation.
	 */
	private fun bumpTranslations(translations: List<DbItem>, answer: String?, correct: Boolean) {
		if (correct) ItemRepository.bumpSf(creditedTranslations(translations, answer).map { it.id }, true)
		else ItemRepository.bumpSf(translations.map { it.id }, false)
	}

	/**
	 * Gives the translations for the picked testing objects.
	 *
	 * @param index index of the tested word which translations you want to get
	 * @return the word's translations
	 */
	fun getTested(index: Int): List<DbItem> =
		if (index >= source.size) emptyList() else source[index].translations()

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
		 * Expresses how much the given word needs to be retested. Computed as the word's
		 * failure rate (split into belts of [RATIO_BELT] percents) minus the amount
		 * of times the word was already tested, so the more tests were run on a word,
		 * the further it slips back in the test queue. This way even words answered
		 * correctly on the first try eventually resurface.
		 *
		 * @return the urgency value, higher means the word should be tested sooner
		 */
		@JvmStatic
		fun urgency(word: DbItem): Int {
			val rat = word.ratio
			return (if (rat < 0) UNTESTED_BELT else (100 - rat) / RATIO_BELT) - word.sfCount
		}

		/**
		 * Picks the [amount] most urgent words from the source. All words as urgent
		 * as the last word still needed are included and then trimmed randomly, so
		 * equally urgent words take turns. Always returns exactly [amount] words
		 * (as long as `amount <= source.size`), regardless of how the words'
		 * urgencies are distributed.
		 */
		@JvmStatic
		fun cleverTest(source: List<TestEntry>, amount: Int): List<TestEntry> {
			val sorted = ArrayList(source)
			sorted.sortWith { a, b -> urgency(b.word).compareTo(urgency(a.word)) }
			val cut = urgency(sorted[amount - 1].word)
			var end = amount
			while (end < sorted.size && urgency(sorted[end].word) == cut) end++
			return rndTest(ArrayList(sorted.subList(0, end)), amount)
		}

		@JvmStatic
		fun rndTest(source: MutableList<TestEntry>, amount: Int): MutableList<TestEntry> {
			val rnd = Random()
			for (i in source.size downTo amount + 1)
				source.removeAt(rnd.nextInt(source.size))
			Collections.shuffle(source)
			return source
		}

		/**
		 * Converts the selected sources into the tested entries: a selected word
		 * is tested directly, a selected chapter is expanded into the words under
		 * it (recursively). Each item becomes at most one entry — a word linked
		 * under sibling chapters (the reference model) is one word, not one per
		 * container that reaches it. The former engine reference resolution is
		 * gone — in the link model an item simply appears under each of its
		 * parents.
		 *
		 * @param sources the selected rows, each with its container chain
		 * (root first, the selected row last)
		 * @param children the container-children lookup, the repository by
		 * default — a fake tree in tests
		 */
		@JvmStatic
		@JvmOverloads
		fun convertAll(
			sources: List<List<DbItem>>,
			children: (Int) -> List<DbItem> = ItemRepository::children,
		): List<TestEntry> {
			val ret = ArrayList<TestEntry>()
			val visited = HashSet<Int>()
			for (path in sources) {
				val last = path.last()
				if (last.type == ItemKind.WORD) {
					if (visited.add(last.id)) ret.add(TestEntry(last, path))
				} else if (last.type == ItemKind.CHAPTER) convertContent(path, children, visited, ret)
			}
			return ret
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

		/** Default time for one item in a test. */
		private var defaultTime = 18

		@JvmStatic
		fun getDefaultTime(): Int = defaultTime

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

		/** The amount of items in a test. */
		@JvmStatic
		var amount = 10

		/**
		 * Whether [answer] names the tested [word] in the current direction: the
		 * word's own name by default, one of its [translations] when [isReversed]
		 * is on. The `/()` variants of every compared name count as answers too.
		 *
		 * This is the side-independent part of the per-run `isAnswer` matching; the
		 * linked-word fallback of the default direction needs the database and
		 * stays there.
		 */
		@JvmStatic
		fun matchesAnswer(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
			if (answer.isNullOrEmpty()) return false
			return if (isReversed) translations.any { matchesName(it.name, answer) }
			else matchesName(word.name, answer)
		}

		/**
		 * The translations of an item that a correct [answer] demonstrated
		 * knowledge of: the one it named in a reversed test, the whole shown
		 * prompt in the normal direction.
		 */
		@JvmStatic
		fun creditedTranslations(translations: List<DbItem>, answer: String?): List<DbItem> =
			if (isReversed) translations.filter { matchesName(it.name, answer ?: "") } else translations

		/** Whether [answer] equals [name] or one of its `/()` variants. */
		private fun matchesName(name: String, answer: String): Boolean =
			answer == name || WordDao.parseVariants(name).any { it == answer }
	}
}
