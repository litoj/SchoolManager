package cz.litoj.schlmgr.testing

/** One required answer slot, owned by one tested word. */
class TestAnswerField(
	val owner: TestItemModel,
	/** Valid answer variants, empty for the ordinary single-answer field. */
	val validVariants: Set<String> = emptySet(),
)
