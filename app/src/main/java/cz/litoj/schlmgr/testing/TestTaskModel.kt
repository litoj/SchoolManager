package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.WordNames

/** One shared prompt and the answer fields that depend on it. */
class TestTaskModel(
	val members: List<TestItemModel>,
	/** The index of the first answer slot in the flattened answer and grading order. */
	val startIndex: Int,
) {

	val answerFields: List<TestAnswerField> = if (TestEngine.requireAllVariants) {
		members.flatMap { member ->
			val names = if (TestEngine.isReversed) member.translations.map { it.name }
			else listOf(member.word.name)
			names.flatMap { name ->
				val variants = WordNames.parseVariants(name).mapTo(HashSet()) { it.trim() }
				List(WordNames.requiredVariantCount(name)) { TestAnswerField(member, variants) }
			}
		}
	} else members.map { TestAnswerField(it) }

	/**
	 * Assigns the typed answers to members, so their field order does not decide.
	 * - ordinary fields need distinct answers that the member accepts.
	 * - variant fields accept any valid variant, so a shared variant can repeat.
	 */
	fun assign(answers: List<String>): Map<TestItemModel, List<String>> =
		if (TestEngine.requireAllVariants) assignVariants(answers) else assignAnswers(answers)

	private fun assignAnswers(answers: List<String>): Map<TestItemModel, List<String>> {
		val texts = answers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
		val matched = maximumMatching(members.size, texts.size) { member, text ->
			val model = members[member]
			TestEngine.matchesEntry(model.word, model.translations, texts[text])
		}
		return members.mapIndexed { index, member ->
			member to listOf(if (matched[index] >= 0) texts[matched[index]] else "")
		}.toMap()
	}

	private fun assignVariants(answers: List<String>): Map<TestItemModel, List<String>> {
		val texts = answers.map { it.trim() }
		val matched = maximumMatching(answerFields.size, texts.size) { slot, text ->
			texts[text] in answerFields[slot].validVariants
		}
		val slotTexts = Array(answerFields.size) { slot ->
			if (matched[slot] >= 0) texts[matched[slot]] else ""
		}
		return members.associateWith { member ->
			answerFields.mapIndexedNotNull { slot, field ->
				slotTexts[slot].takeIf { field.owner === member }
			}
		}
	}

	/** One bipartite matching, where every left node takes at most one right one. */
	private fun maximumMatching(
		leftCount: Int,
		rightCount: Int,
		edge: (Int, Int) -> Boolean,
	): IntArray {
		val rightOwner = IntArray(rightCount) { -1 }
		fun augment(left: Int, seen: BooleanArray): Boolean {
			for (right in 0 until rightCount) {
				if (seen[right] || !edge(left, right)) continue
				seen[right] = true
				val owner = rightOwner[right]
				if (owner == -1 || augment(owner, seen)) {
					rightOwner[right] = left
					return true
				}
			}
			return false
		}
		for (left in 0 until leftCount) augment(left, BooleanArray(rightCount))
		val matched = IntArray(leftCount) { -1 }
		for (right in 0 until rightCount) {
			val left = rightOwner[right]
			if (left >= 0) matched[left] = right
		}
		return matched
	}

	companion object {

		/** Maps engine groups onto models while keeping their absolute answer indices. */
		fun flatten(models: List<TestItemModel>, groups: List<TestTask>): List<TestTaskModel> {
			val tasks = ArrayList<TestTaskModel>(groups.size)
			var next = 0
			for (group in groups) {
				val first = next
				tasks.add(TestTaskModel(group.entries.map { models[next++] }, first))
			}
			return tasks
		}
	}
}
