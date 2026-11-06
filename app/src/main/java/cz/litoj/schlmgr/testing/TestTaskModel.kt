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
			val expected = if (TestEngine.isReversed)
				member.translations.flatMap { WordNames.parseVariants(it.name) }
			else WordNames.parseVariants(member.word.name)
			expected.map { TestAnswerField(member, it.trim()) }
		}
	} else members.map { TestAnswerField(it) }

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
