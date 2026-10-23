package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import java.util.regex.Pattern

/**
 * The pure query parsing and matching engine, with no Views, threads or history.
 * - it matches against a preloaded [SearchIndex], synchronously on the caller thread.
 * - a superseded run aborts via [abort].
 * - a leading backslash selects structured search: type, value source and op tail.
 * - the type is W, T or C, and the value source D, S, F, N or R.
 * - see the resolve cases below.
 */
class SearchEngine(query: String) {

	/** False when [query] had a bad backslash r regex. */
	var valid = true
		private set

	/**
	 * The caller turns this into the user message, since the engine shows nothing.
	 */
	var regexError: String? = null
		private set

	/** All items already reported, which dedups the emitted results by row id. */
	private val found = HashSet<Int>()

	/**
	 * Containers already walked this run.
	 * - an item placed in more containers is emitted under the first one walked only.
	 * - [correct] dedups by row id, and the subtree of a shared chapter is walked once.
	 */
	private val visited = HashSet<Int>()

	val visitedChapters: Int get() = visited.size

	/**
	 * The walk abandons itself when the user navigates away or starts a new search.
	 * - the view model lowers it when the search stops displaying.
	 */
	@Volatile
	private var active = true

	val isActive: Boolean get() = active

	/** Marks the run superseded, so the walk ends at its next check. */
	fun abort() {
		active = false
	}

	private fun interface Correct {
		fun verify(item: DbItem): Boolean
	}

	private fun interface SFManipulation {
		fun value(item: DbItem): Int
	}

	private fun interface StringFinder {
		fun verify(str: String): Boolean
	}

	private var compareText: String = ""

	private val matcher: Correct = resolve(query)

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

		// an empty query matches everything, since compareText stays empty
		if (query.isNotEmpty() && query[start] == '\\' && query.length > 1) {
			when (query[++start]) { // the type marker first, since a query may target one kind
				'W' -> kind = Correct { item -> item.type == ItemKind.WORD }
				'T' -> kind = Correct { item -> item.type == ItemKind.TRANSLATION }
				'C' -> kind = Correct { item -> item.type == ItemKind.CHAPTER }
				else -> start-- // no type selection prefix found
			}
			if (start + 1 >= query.length) return emptyCompare()
			when (query[++start]) { // then the value marker, since one query tests one source
				// d compares the description, so text queries can target it
				'D' -> searchDescription = true
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
			// the operator marker, since text and number tests share one syntax
			when (query[start + 1]) {
				// number operations
				'>' -> numeric = numberCompare(sfm, count) { a, b -> a > b }
				'<' -> numeric = numberCompare(sfm, count) { a, b -> a < b }
				'=' -> numeric = numberCompare(sfm, count) { a, b -> a == b }
				// text operations
				'r' -> { // r compiles the text, since a pattern may match either field
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
				// must contain the written text, ignores case
				'\\' -> compareText = compareText.lowercase()
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

	private fun nameMatcher(kind: Correct, sc: StringFinder): Correct = Correct { item ->
		if (!kind.verify(item)) return@Correct false
		if (sc.verify(item.name)) return@Correct true
		for (name in WordNames.parseVariants(item.name)) if (sc.verify(name)) return@Correct true
		false
	}

	/**
	 * @param path container chain, direct parent last
	 * @param onMatch called once per new match with a copy of path
	 * @return whether the item matched, even if already emitted
	 */
	fun correct(item: DbItem, path: List<DbItem>, onMatch: (DbItem, List<DbItem>) -> Unit): Boolean {
		val yes = matcher.verify(item)
		if (yes && found.add(item.id)) onMatch(item, ArrayList(path))
		return yes
	}

	/**
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
				ItemKind.WORD -> if (!foundIt) {
					// a word that missed its own name is retried through its translations.
					// the WORD is the hit, so its own row and path show
					if (index.childrenOf(item.id).any { matcher.verify(it) } && found.add(item.id))
						onMatch(item, ArrayList(path))
				}
				else -> {}
			}
		}
	}
}
