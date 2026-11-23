package cz.litoj.schlmgr.ui.search

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.data.db.testedCount
import java.util.regex.Pattern

/**
 * The pure matching engine, with no Views, threads or history.
 * - it reads a [SearchQuery] the caller built, and matches against a preloaded [SearchIndex].
 * - a scope limits the rows it reports, so a selection can own a run, see [search].
 * - a superseded run aborts via [abort].
 * - the typed text and every criterion must hold, so a run answers to all of them.
 * - [folded] folds the text finders, a pattern stays as written.
 */
class SearchEngine(query: SearchQuery, private val foldDiacritics: Boolean = false) {

	/** All items already reported, which dedups the emitted results by row id. */
	private val found = HashSet<Int>()

	/**
	 * Containers already walked this run.
	 * - an item placed in more containers is emitted under the first one walked only.
	 * - [correct] dedups by row id, and the subtree of a shared chapter is walked once.
	 */
	private val visited = HashSet<Int>()

	/**
	 * The rows the run may report, empty for every row of the container.
	 * - the caller closes it over its seeds, so the walk reads membership only.
	 */
	private var scope: Set<Int> = emptySet()

	private fun inScope(item: DbItem) = scope.isEmpty() || item.id in scope

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

	private fun interface StringFinder {
		fun verify(str: String): Boolean
	}

	/** The candidate text under the fold setting, see the toggle in the settings. */
	private fun folded(text: String): String =
		if (foldDiacritics) DiacriticFold.of(text) else text

	/**
	 * The parts a translation may answer for its word: the typed text, names and descriptions.
	 * - the counters belong to the word, so a translation cannot invent them.
	 */
	private val textParts: Correct

	/** The counters only the word itself answers, so a translation cannot invent them. */
	private val ownParts: Correct

	/** The kind of a row, applied to the candidate row, never assumed from its parent word. */
	private val typePart: (DbItem) -> Boolean

	init {
		val text = ArrayList<Correct>()
		val own = ArrayList<Correct>()
		val types = ArrayList<Correct>()
		val typed = query.text.trim()
		// the typed text is a name search, the criteria add their own comparisons
		if (typed.isNotEmpty()) text.add(nameTest(FilterOperator.CONTAINS, typed))
		for (criterion in query.criteria) {
			val test = criterionTest(criterion)
			when (criterion.tag) {
				SearchTag.TYPE -> types.add(test)
				SearchTag.NAME, SearchTag.DESCRIPTION -> text.add(test)
				else -> own.add(test)
			}
		}
		textParts = Correct { item -> text.all { it.verify(item) } }
		ownParts = Correct { item -> own.all { it.verify(item) } }
		typePart = { item -> types.all { it.verify(item) } }
	}

	/** Every part of the query must hold, so the run answers to all of them. */
	private val matcher: Correct = Correct { item ->
		ownParts.verify(item) && typePart(item) && textParts.verify(item)
	}

	private fun criterionTest(criterion: Criterion): Correct {
		val value = criterion.value
		return when (criterion.tag) {
			// a value of another shape compares nothing, the editor pairs them at build
			SearchTag.TYPE -> if (value is CriterionValue.Kind)
				Correct { item -> item.type == value.value }
			else none()
			SearchTag.NAME -> nameTest(criterion.operator, textOf(value))
			SearchTag.DESCRIPTION -> descriptionTest(criterion.operator, textOf(value))
			SearchTag.RATE -> if (value is CriterionValue.Rate) rateTest(criterion.operator, value) else none()
			SearchTag.TESTED -> if (value is CriterionValue.Times) timesTest(criterion.operator, value) else none()
		}
	}

	private fun textOf(value: CriterionValue) = (value as? CriterionValue.Text)?.value ?: ""

	private fun none() = Correct { _ -> false }

	/** A name search reads the row name and the names its variants hide. */
	private fun nameTest(operator: FilterOperator, value: String): Correct {
		val finder = stringFinder(operator, value) ?: return none()
		return nameMatcher(finder, exact = operator == FilterOperator.CONTAINS_CASE)
	}

	/** A description search skips the rows without one. */
	private fun descriptionTest(operator: FilterOperator, value: String): Correct {
		val finder = stringFinder(operator, value) ?: return none()
		// the exact-case contains reads the stored whitespace too
		val exact = operator == FilterOperator.CONTAINS_CASE
		return Correct { item ->
			val text = if (exact) item.description else item.description.trim()
			text.isNotEmpty() && finder.verify(text)
		}
	}

	/** null for an operator that compares a number or a kind, never text. */
	private fun stringFinder(operator: FilterOperator, value: String): StringFinder? {
		// a pattern is the user's own text, so the fold never rewrites it
		if (operator == FilterOperator.REGEX) return patternFinder(value)
		return when (operator) {
			FilterOperator.CONTAINS -> {
				val text = folded(value.lowercase())
				StringFinder { name -> folded(name).lowercase().contains(text) }
			}
			FilterOperator.CONTAINS_CASE -> {
				val text = folded(value)
				StringFinder { name -> folded(name).contains(text) }
			}
			FilterOperator.STARTS -> {
				val text = folded(value)
				StringFinder { name -> folded(name).startsWith(text) }
			}
			FilterOperator.ENDS -> {
				val text = folded(value)
				StringFinder { name -> folded(name).endsWith(text) }
			}
			else -> null
		}
	}

	/** A pattern the editor did not compile matches nothing, so it never widens a run. */
	private fun patternFinder(pattern: String): StringFinder {
		val compiled = try {
			Pattern.compile(pattern)
		} catch (e: Exception) {
			return StringFinder { false }
		}
		return StringFinder { name -> compiled.matcher(name).matches() }
	}

	/**
	 * The rate test of one criterion.
	 * - a joining untested word counts as zero, since a rate of zero is typeable.
	 * - only a word carries counters, so a container never answers a number.
	 */
	private fun rateTest(operator: FilterOperator, rate: CriterionValue.Rate): Correct {
		val test = comparison(operator) ?: return none()
		return Correct { item ->
			val percent = item.ratio
			when {
				item.type != ItemKind.WORD -> false
				percent < 0 -> rate.untested == UntestedRows.INCLUDE && test(0, rate.percent)
				else -> test(percent, rate.percent)
			}
		}
	}

	/** The count test of one criterion, where zero is a value of its own and only a word carries counters. */
	private fun timesTest(operator: FilterOperator, times: CriterionValue.Times): Correct {
		val test = comparison(operator) ?: return none()
		return Correct { item -> item.type == ItemKind.WORD && test(item.testedCount, times.value) }
	}

	/** null for an operator that compares text or a kind, never a number. */
	private fun comparison(operator: FilterOperator): ((Int, Int) -> Boolean)? = when (operator) {
		FilterOperator.GREATER -> { a, b -> a > b }
		FilterOperator.AT_LEAST -> { a, b -> a >= b }
		FilterOperator.LESS -> { a, b -> a < b }
		FilterOperator.AT_MOST -> { a, b -> a <= b }
		FilterOperator.EQUALS -> { a, b -> a == b }
		else -> null
	}

	private fun nameMatcher(sc: StringFinder, exact: Boolean): Correct = Correct { item ->
		val name = if (exact) item.name else item.name.trim()
		if (sc.verify(name)) return@Correct true
		for (variant in WordNames.parseVariants(name)) {
			if (sc.verify(if (exact) variant else variant.trim())) return@Correct true
		}
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
	 * @param scope ids the run may report, empty for the whole container
	 * @param onMatch called once per new match with a copy of path
	 */
	fun search(
		index: SearchIndex,
		basePath: List<DbItem>,
		scope: Set<Int> = emptySet(),
		onMatch: (item: DbItem, path: List<DbItem>, matchedTranslation: Boolean) -> Unit,
	) {
		val root = basePath.lastOrNull() ?: return
		this.scope = scope
		walk(index, index.childrenOf(root.id), basePath, onMatch)
	}

	private fun walk(
		index: SearchIndex,
		src: List<DbItem>,
		path: List<DbItem>,
		onMatch: (DbItem, List<DbItem>, Boolean) -> Unit,
	) {
		loop@ for (item in src) {
			if (!active) return
			val yes = inScope(item) && matcher.verify(item)
			val foundIt = yes && found.add(item.id)
			if (foundIt) onMatch(item, ArrayList(path), false)
			when (item.type) {
				ItemKind.CHAPTER -> {
					if (!visited.add(item.id)) continue@loop
					val list = ArrayList<DbItem>(path.size + 1)
					list.addAll(path)
					list.add(item)
					walk(index, index.childrenOf(item.id), list, onMatch)
				}
				ItemKind.WORD -> if (!yes && inScope(item) && ownParts.verify(item)) {
					// a word that missed its own text is retried through its translations.
					// the WORD is the hit, so its own row and path show
					if (index.childrenOf(item.id).any { typePart(it) && textParts.verify(it) } && found.add(item.id))
						onMatch(item, ArrayList(path), true)
				}
				else -> {}
			}
		}
	}
}
