package cz.litoj.schlmgr.ui.explorer.search

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.dao.WordDao
import cz.litoj.schlmgr.db.ratio
import cz.litoj.schlmgr.db.sfCount
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.app.Startup
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Pure query-parsing + matching engine ported out of the legacy
 * `ExplorerStuff.SearchControl.SearchEngine` inner class. It contains **no**
 * View, adapter, thread-spawning, or `BackLog` knowledge — the owning
 * ExplorerController preloads the searched content ([SearchIndex]) and consumes
 * the hits; everything else (the prefix grammar, the word-translate fallback,
 * the visited/found sets) is reproduced here byte-for-byte.
 *
 * ### Query grammar (unchanged from the legacy `resolver:` block)
 * An optional leading `\` prefix enables structured search; without it the whole
 * query is a case-insensitive *contains* match on the item's name and its
 * [WordDao.parseVariants] fallbacks:
 * ```
 * \ [W|T|C]? [D|S|F|N|R]? OP TAIL
 * ```
 * - `W`/`T` — main-word / translate-only, `C` — any chapter; otherwise no type
 *   filter.
 * - `D` — match against the description, `S`/`F` — success/fail counts,
 *   `N` — times tested, `R` — ratio; otherwise the name (+parseVariants fallbacks).
 * - `OP` is `>` `<` `=` (numeric, `TAIL` parsed as an `Int`, unparseable → −10)
 *   or `r` `s` `e` `c` `\` (regex / startsWith / endsWith / contains /
 *   case-insensitive-contains). Anything else is a case-insensitive *contains*
 *   of the text from `OP` on. A bad regex shows the legacy
 *   [R.string.pattern_err] snackbar and aborts the run ([valid] is `false`).
 *
 * The engine itself spawns no threads and touches no Views; a run filters the
 * preloaded [SearchIndex] synchronously on the caller's thread. The shared
 * mutable state ([visited]/[found]) stays thread-safe for aborts from other
 * threads ([active] is checked during the walk).
 */
class SearchEngine(query: String) {

	/** `false` when [query] had a bad `\ … r` regex (legacy `return`ed from the ctor). */
	var valid = true
		private set

	/** All items already reported (legacy `set`; dedups the emitted results) — by row id. */
	private val found = ConcurrentHashMap.newKeySet<Int>()

	/**
	 * Containers already walked this run (legacy `visited`). An item placed in
	 * more containers appears under each of them — the walk enters its subtree
	 * once (first parent wins) so shared chapters aren't re-matched repeatedly.
	 */
	private val visited = ConcurrentHashMap.newKeySet<Int>()

	/**
	 * Live-run flag. The legacy `search()` loop checked
	 * `backLog.adapter instanceof SearchAdapter` on every item and abandoned the
	 * whole walk the moment the user navigated away / started a new search —
	 * this is that check, decoupled from the adapter. The controller sets it
	 * `false` when the current search stops being the displayed one.
	 */
	@Volatile
	var active = true

	// --- the legacy matching primitives -------------------------------------------------
	private fun interface Correct {
		fun verify(bd: DbItem): Boolean
	}

	private fun interface SFManipulation {
		fun value(bd: DbItem): Int
	}

	private fun interface StringFinder {
		fun verify(str: String): Boolean
	}

	/** The compare text set up by the prefix resolver (legacy `comp` field). */
	private var comp: String = ""

	/** The final comparator the legacy ctor assembled into `correct`. */
	private val correct: Correct = resolve(query)

	/**
	 * Whole legacy ctor body up to `backLog.add(...)`. Uses labeled returns in place of
	 * the Java `break resolver;` / `return;` (bad-regex): the local `emptyCompare()` is
	 * the `comp = ""; break resolver;` early-exit, factored out so it can be `return`ed.
	 */
	private fun resolve(query: String): Correct {
		var cor1: Correct? = Correct { _ -> true }
		var cor2: Correct? = null
		var sfm: SFManipulation? = null
		var strCor: StringFinder? = StringFinder { name -> name.lowercase().contains(comp) }
		var start = 0
		var desc = false

		// The two `comp = ""; break resolver;` early-exits of the Java labelled block.
		fun emptyCompare(): Correct {
			comp = ""
			val c1 = cor1
			val sc = strCor
			return Correct { bd ->
				if (c1 != null && !c1.verify(bd)) return@Correct false
				if (sc!!.verify(bd.name)) return@Correct true
				for (name in WordDao.parseVariants(bd.name)) if (sc.verify(name)) return@Correct true
				false
			}
		}

		// `query[0]` on an empty query was the empty-search crash; an empty query
		// simply matches everything (`comp` stays empty).
		if (query.isNotEmpty() && query[start] == '\\' && query.length > 1) {
			when (query[++start]) { // select the type of searched object
				'W' -> cor1 = Correct { bd -> bd.type == ItemKind.WORD }
				'T' -> cor1 = Correct { bd -> bd.type == ItemKind.TRANSLATION }
				'C' -> cor1 = Correct { bd -> bd.type == ItemKind.CHAPTER }
				else -> start-- // no type selection prefix found
			}
			if (start + 1 >= query.length) return emptyCompare()
			when (query[++start]) { // select the source of comparing
				'D' -> desc = true // the tested value is the object's description
				// for selection by success rate
				'S' -> sfm = SFManipulation { bd -> bd.passedTests }
				'F' -> sfm = SFManipulation { bd -> bd.failedTests }
				'N' -> sfm = SFManipulation { bd -> bd.sfCount }
				'R' -> sfm = SFManipulation { bd -> bd.ratio }
				else -> start-- // the tested value is the name of the object
			}
			if (start + 1 >= query.length) return emptyCompare()
			val count = try {
				query.substring(start + 2).toInt()
			} catch (nfe: NumberFormatException) {
				-10
			}
			val copyOC = cor1
			val copySFM = sfm
			comp = query.substring(start + 2)
			when (query[start + 1]) { // the main select operation prefix selector
				// number operations
				'>' -> cor2 = if (copyOC == null)
					Correct { bd -> copySFM!!.value(bd) > count }
				else Correct { bd -> copyOC.verify(bd) && copySFM!!.value(bd) > count }
				'<' -> cor2 = if (copyOC == null)
					Correct { bd -> copySFM!!.value(bd) < count }
				else Correct { bd -> copyOC.verify(bd) && copySFM!!.value(bd) < count }
				'=' -> cor2 = if (copyOC == null)
					Correct { bd -> copySFM!!.value(bd) == count }
				else Correct { bd -> copyOC.verify(bd) && copySFM!!.value(bd) == count }
				// text operations
				'r' -> { // if the object's name (or description) matches given regex
					val p: Pattern = try {
						Pattern.compile(comp)
					} catch (e: Exception) {
						val msg = GlobalDependencies.appContext.getString(R.string.pattern_err) +
							'\n' + e.message
						Startup.showMsg(msg, msg)
						valid = false
						return Correct { _ -> false } // legacy `return;` — aborts the run
					}
					strCor = StringFinder { name -> p.matcher(name).matches() }
				}
				's' -> strCor = StringFinder { name -> name.startsWith(comp) }
				'e' -> strCor = StringFinder { name -> name.endsWith(comp) }
				'c' -> strCor = StringFinder { name -> name.contains(comp) }
				'\\' -> comp = comp.lowercase() // must contain the written text, ignores case
				else -> comp = query.lowercase().substring(start + 1)
			}
		} else comp = query.lowercase()

		// constructs the final search comparator
		val copyC1 = cor1
		val copyC2 = cor2
		val copySC = strCor
		return if (sfm != null && copyC2 != null)
			Correct { bd -> copyC1!!.verify(bd) && copyC2.verify(bd) }
		else if (desc) Correct { bd ->
			if (copyC1 != null && !copyC1.verify(bd)) return@Correct false
			val d = bd.description
			d.isNotEmpty() && copySC!!.verify(d)
		}
		else Correct { bd ->
			if (copyC1 != null && !copyC1.verify(bd)) return@Correct false
			if (copySC!!.verify(bd.name)) return@Correct true
			for (name in WordDao.parseVariants(bd.name)) if (copySC.verify(name)) return@Correct true
			false
		}
	}

	/**
	 * The per-item validation the legacy `correct(bd, path)` performed: applies the
	 * comparator and, on a (new) match, emits the item exactly once. `path` is the
	 * container chain owning [bd] (last element = its direct parent); the callback
	 * receives a defensive copy, as the legacy code did for its `rv.post` closure.
	 *
	 * @return whether [bd] matched (regardless of whether it had been emitted before).
	 */
	fun correct(bd: DbItem, path: List<DbItem>, onMatch: (DbItem, List<DbItem>) -> Unit): Boolean {
		val yes = correct.verify(bd)
		if (yes && found.add(bd.id)) onMatch(bd, ArrayList(path))
		return yes
	}

	/**
	 * Searches the whole content of the currently opened container: [basePath]
	 * is its container chain (root first, the opened container last) and [index]
	 * the preloaded subgraph below it. Faithful port of the legacy `search(...)`
	 * walk, with the data sourcing changed from per-chapter database queries to
	 * the preloaded [SearchIndex]:
	 *
	 * - the `backLog.adapter instanceof SearchAdapter` early-abort stays [active],
	 * - the legacy thread fan-out is gone — filtering the in-memory index needs
	 *   no workers, so the run is a plain loop,
	 * - the legacy `Reference` deref walk is gone: in the link model an item simply
	 *   appears under each of its parents, so [visited] (by item id) keeps every shared
	 *   chapter's subtree from being walked twice. The word-translate fallback is
	 *   verbatim: a word that itself didn't match is still found through any of its
	 *   translations (its children in the index).
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
					for (trl in index.childrenOf(item.id))
						correct(trl, path, onMatch)
				else -> {}
			}
		}
	}
}
