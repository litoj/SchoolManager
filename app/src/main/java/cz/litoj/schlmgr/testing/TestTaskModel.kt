package cz.litoj.schlmgr.testing

/** One shared prompt and the answer fields that depend on it. */
class TestTaskModel(
	val members: List<TestItemModel>,
	/** The index of the first member in the flattened answer and grading order. */
	val startIndex: Int,
) {

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
