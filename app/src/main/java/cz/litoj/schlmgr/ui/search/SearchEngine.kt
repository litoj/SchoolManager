package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import java.util.regex.Pattern

/**
 * Pure query-parsing + matching engine — no Views, threads or history.
 * - matches against a preloaded [SearchIndex], synchronously on the caller
 *   thread. A superseded run aborts via [active].
 * - a leading `\` selects structured search: `[W|T|C]? [D|S|F|N|R]? OP TAIL`
 *   (type, value source, op) — see the `resolve` cases below.
 */
class SearchEngine(query: String) {

	/** `false` when [query] had a bad `\ … r` regex. */
	var valid = true
		private set

	/**
	 * The regex failure detail when [valid] is `false` (the exception message
	 * of the failed compile), `null` otherwise. The engine shows nothing
	 * itself — the caller turns this into the user-facing message.
	 */
	var regexError: String? = null
		private set

	/** All items already reported — dedups the emitted results, by row id. */
	private val found = HashSet<Int>()

	/**
	 * Containers already walked this run. An item placed in more containers
	 * is emitted under the first container walked only — [correct] dedups by
	 * row id — and the subtree of a shared chapter is walked once.
	 */
	private val visited = HashSet<Int>()

	/**
	 * Live-run flag — the walk abandons itself the moment the user navigates
	 * away or starts a new search. The view model lowers it when the search
	 * stops displaying.
	 */
	@Volatile
	var active = true

	// ------------------------------------------------------------ matchers
	private fun interface Correct {
		fun verify(item: DbItem): Boolean
	}

	private fun interface SFManipulation {
		fun value(item: DbItem): Int
	}

	private fun interface StringFinder {
		fun verify(str: String): Boolean
	}

	/** Prefix resolver's compare text. */
	private var compareText: String = ""

	private val matcher: Correct = resolve(query)

	/**
	 * Resolves the query into a matcher — an optional type prefix (`\W`,
	 * `\T`, `\C`), an optional value-source prefix (`D`, `S`, `F`, `N`,
	 * `R`), then the operation selector with its tail as the compare text.
	 * `emptyCompare()` is the early exit for a prefix-only query.
	 */
	private fun resolve(query: String): Correct {
		var kind: Correct = Correct { _ -> true }
		var numeric: Correct? = null
		var sfm: SFManipulation? = null
		var strCor: StringFinder = StringFinder { name -> name.lowercase().contains(compareText) }
		var start = 0
		var searchDescription = false

		fun emptyCompare(): Correct {
			compareText = ""
			return nameMatcher(kind, strCor)
		}

		// an empty query simply matches everything (`compareText` stays empty)
		if (query.isNotEmpty() && query[start] == '\\' && query.length > 1) {
			when (query[++start]) { // select the type of searched object
				'W' -> kind = Correct { item -> item.type == ItemKind.WORD }
				'T' -> kind = Correct { item -> item.type == ItemKind.TRANSLATION }
				'C' -> kind = Correct { item -> item.type == ItemKind.CHAPTER }
				else -> start-- // no type selection prefix found
			}
			if (start + 1 >= query.length) return emptyCompare()
			when (query[++start]) { // select the source of comparing
				'D' -> searchDescription = true // the tested value is the object's description
				'S' -> sfm = SFManipulation { item -> item.passedTests }
				'F' -> sfm = SFManipulation { item -> item.failedTests }
				'N' -> sfm = SFManipulation { item -> item.testedCount }
				'R' -> sfm = SFManipulation { item -> item.ratio }
				else -> start-- // the tested value is the name of the object
			}
			if (start + 1 >= query.length) return emptyCompare()
			val count = try {
				query.substring(start + 2).toInt()
			} catch (nfe: NumberFormatException) {
				-10
			}
			compareText = query.substring(start + 2)
			when (query[start + 1]) { // the main select operation prefix selector
				// number operations
				'>' -> numeric = numberCompare(sfm, count) { a, b -> a > b }
				'<' -> numeric = numberCompare(sfm, count) { a, b -> a < b }
				'=' -> numeric = numberCompare(sfm, count) { a, b -> a == b }
				// text operations
				'r' -> { // if the object's name (or description) matches given regex
					val p: Pattern = try {
						Pattern.compile(compareText)
					} catch (e: Exception) {
						valid = false
						regexError = e.message
						return Correct { _ -> false }
					}
					strCor = StringFinder { name -> p.matcher(name).matches() }
				}
				's' -> strCor = StringFinder { name -> name.startsWith(compareText) }
				'e' -> strCor = StringFinder { name -> name.endsWith(compareText) }
				'c' -> strCor = StringFinder { name -> name.contains(compareText) }
				'\\' -> compareText = compareText.lowercase() // must contain the written text, ignores case
				else -> compareText = query.lowercase().substring(start + 1)
			}
		} else compareText = query.lowercase()

		val finalNumeric = numeric
		return if (finalNumeric != null)
			Correct { item -> kind.verify(item) && finalNumeric.verify(item) }
		else if (searchDescription) Correct { item ->
			if (!kind.verify(item)) return@Correct false
			val d = item.description
			d.isNotEmpty() && strCor.verify(d)
		}
		else nameMatcher(kind, strCor)
	}

	private fun numberCompare(
		sfm: SFManipulation?, count: Int, test: (Int, Int) -> Boolean,
	): Correct {
		val source = sfm ?: SFManipulation { _ -> 0 }
		return Correct { item -> test(source.value(item), count) }
	}

	/** Matches an item's name, then each parsed variant of it. */
	private fun nameMatcher(kind: Correct, sc: StringFinder): Correct = Correct { item ->
		if (!kind.verify(item)) return@Correct false
		if (sc.verify(item.name)) return@Correct true
		for (name in WordNames.parseVariants(item.name)) if (sc.verify(name)) return@Correct true
		false
	}

	/**
	 * Validates one item and emits it once on a new match.
	 *
	 * @param path container chain, direct parent last
	 * @param onMatch called once per new match with a copy of `path`
	 * @return whether the item matched, even if already emitted
	 */
	fun correct(item: DbItem, path: List<DbItem>, onMatch: (DbItem, List<DbItem>) -> Unit): Boolean {
		val yes = matcher.verify(item)
		if (yes && found.add(item.id)) onMatch(item, ArrayList(path))
		return yes
	}

	/**
	 * Searches the whole content of the currently opened container.
	 *
	 * @param basePath container chain, root first, opened container last
	 */
	fun search(
		index: SearchIndex,
		basePath: List<DbItem>,
		onMatch: (DbItem, List<DbItem>) -> Unit,
	) {
		val root = basePath.lastOrNull() ?: return
		walk(index, index.childrenOf(root.id), basePath, onMatch)
	}

	private fun walk(
		index: SearchIndex,
		src: List<DbItem>,
		path: List<DbItem>,
		onMatch: (DbItem, List<DbItem>) -> Unit,
	) {
		loop@ for (item in src) {
			if (!active) return
			val foundIt = correct(item, path, onMatch)
			when (item.type) {
				ItemKind.CHAPTER -> {
					if (!visited.add(item.id)) continue@loop
					val list = ArrayList<DbItem>(path.size + 1)
					list.addAll(path)
					list.add(item)
					walk(index, index.childrenOf(item.id), list, onMatch)
				}
				ItemKind.WORD -> if (!foundIt)
					// a word that did not match is retried through its translations
					for (trl in index.childrenOf(item.id))
						correct(trl, path, onMatch)
				else -> {}
			}
		}
	}
}
