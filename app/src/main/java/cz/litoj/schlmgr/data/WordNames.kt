package cz.litoj.schlmgr.data

/**
 * Stored-name parsing:
 * - slash alternatives and group parentheses expand through [parseVariants].
 * - backslash-separated editor fields split through [splitEscaped].
 */
object WordNames {

	/**
	 * - a slash separates alternative forms, so each form appears once.
	 * - a group in parentheses expands each alternative with the surrounding text.
	 * - an escaped slash, parenthesis or backslash stays literal.
	 */
	@JvmStatic
	fun parseVariants(name: String): List<String> = variantTree(name).forms.map { it.text }

	/** Minimum valid variants that can cover every side once. */
	@JvmStatic
	fun requiredVariantCount(name: String): Int = variantTree(name).answerCount

	/** Whether supplied valid variants cover every side, including an empty alternative. */
	@JvmStatic
	fun coversAllVariants(name: String, answers: List<String>): Boolean {
		val supplied = answers.mapTo(HashSet()) { it.trim() }
		val tree = variantTree(name)
		val matched = tree.forms.filter { it.text.trim() in supplied }
		val covered = matched.flatMapTo(HashSet()) { it.choices }
		return matched.isNotEmpty() && covered.containsAll(tree.requiredChoices)
	}

	private fun variantTree(name: String): VariantNode = if (name.contains("/")) NameVariantParser(name).result
	else VariantNode(listOf(VariantForm(name, emptySet())), 1, emptySet())

	/**
	 * - a backslash starts a new word, unless it escapes the next character.
	 * - the escaped syntax characters are backslash, slash and both parentheses.
	 * - a trailing backslash stays.
	 */
	@JvmStatic
	fun splitEscaped(name: String): List<String> {
		val words = ArrayList<String>()
		var start = 0
		var i = 0
		while (i < name.length) {
			if (name[i] == '\\') {
				val next = name.getOrNull(i + 1)
				if (next != null && next != '\\' && next != '/' && next != '(' && next != ')') {
					words.add(name.substring(start, i))
					start = i + 1
				} else i++
			}
			i++
		}
		words.add(name.substring(start))
		return words
	}
}

private data class VariantForm(val text: String, val choices: Set<Int>)

private data class VariantNode(
	val forms: List<VariantForm>,
	val answerCount: Int,
	val requiredChoices: Set<Int>,
)

/**
 * Variant parser for slash alternatives and parenthesized groups, with backslash escapes.
 * Each expanded form also carries the side choices it covers.
 */
private class NameVariantParser(private val str: String) {

	private var i = 0
	private var nextChoice = 0

	val result: VariantNode = expression(false)

	private fun expression(group: Boolean): VariantNode {
		val options = ArrayList<VariantNode>()
		var current = text("")
		while (i < str.length) {
			when (val now = str[i]) {
				'/' -> {
					options.add(current)
					current = text("")
					i++
				}
				'(' -> {
					i++
					current = join(current, expression(true))
					if (i < str.length && str[i] == ')') i++
				}
				')' -> if (group) {
					options.add(current)
					return alternatives(options)
				} else {
					current = joinText(current, ")")
					i++
				}
				'\\' -> {
					i++
					// a trailing backslash escapes nothing, so it is dropped, not crashed on
					if (i < str.length) {
						current = joinText(current, str[i].toString())
						i++
					}
				}
				'.', '!', '?', ',', ' ' -> {
					options.add(current)
					val left = joinSeparator(alternatives(options), now.toString())
					i++
					return join(left, expression(group))
				}
				else -> {
					current = joinText(current, now.toString())
					i++
				}
			}
		}
		options.add(current)
		return alternatives(options)
	}

	private fun alternatives(options: List<VariantNode>): VariantNode {
		val kept = options.toMutableList()
		// a trailing empty group or separator adds no empty variant
		while (kept.size > 1 && kept.last().forms.singleOrNull()?.text == ""
			&& kept.last().requiredChoices.isEmpty()
		) kept.removeAt(kept.lastIndex)
		if (kept.size == 1) return kept[0]
		val branchIds = kept.map { nextChoice++ }
		return VariantNode(
			forms = kept.flatMapIndexed { branch, node ->
				node.forms.map { form -> VariantForm(form.text, form.choices + branchIds[branch]) }
			},
			// nested alternatives add, independent sequence positions take the widest one
			answerCount = kept.sumOf { it.answerCount },
			requiredChoices = (branchIds + kept.flatMap { it.requiredChoices }).toSet(),
		)
	}

	private fun joinSeparator(node: VariantNode, separator: String): VariantNode = VariantNode(
		forms = node.forms.map { form ->
			// an empty alternative owns no separator, so the shortest form has no leading space
			val suffix = if (form.text.isEmpty() && node.requiredChoices.isNotEmpty()) "" else separator
			VariantForm(form.text + suffix, form.choices)
		},
		answerCount = node.answerCount,
		requiredChoices = node.requiredChoices,
	)

	private fun joinText(node: VariantNode, suffix: String): VariantNode = join(node, text(suffix))

	private fun join(left: VariantNode, right: VariantNode): VariantNode = VariantNode(
		forms = left.forms.flatMap { a ->
			right.forms.map { b -> VariantForm(a.text + b.text, a.choices + b.choices) }
		},
		answerCount = maxOf(left.answerCount, right.answerCount),
		requiredChoices = left.requiredChoices + right.requiredChoices,
	)

	private fun text(value: String) = VariantNode(listOf(VariantForm(value, emptySet())), 1, emptySet())
}
