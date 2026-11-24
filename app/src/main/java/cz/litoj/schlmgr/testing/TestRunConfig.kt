package cz.litoj.schlmgr.testing

/** Immutable selection and answer configuration captured when one test run is prepared. */
data class TestRunConfig(
	val reversed: Boolean,
	val requireAllVariants: Boolean,
	val ignoredMistakes: List<String>,
	val cleverSelection: Boolean,
) {
	companion object {
		fun current() = TestRunConfig(
			TestEngine.isReversed,
			TestEngine.requireAllVariants,
			TestEngine.ignoredMistakesText.lines().filter { it.isNotEmpty() }.distinct(),
			TestEngine.isClever,
		)
	}
}
