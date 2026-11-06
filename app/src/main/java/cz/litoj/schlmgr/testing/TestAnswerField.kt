package cz.litoj.schlmgr.testing

/** One required answer slot, owned by one tested word. */
class TestAnswerField(
	val owner: TestItemModel,
	/** The expected variant, empty for the ordinary single-answer field. */
	val expected: String = "",
)
