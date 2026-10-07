package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TestEngine]: the content-selection heuristic (the "clever"
 * mode) and the direction-aware answer matching.
 */
class TestEngineSelectionTest {

	/**
	 * A tested word with mutable success stats. The stats live here (a [DbItem] is
	 * immutable), so the repeated-run simulations can update them between rounds;
	 * [toEntry] snapshots them into the plain rows the engine selects from.
	 */
	private class Item(val id: Int, var successes: Int, var fails: Int) {

		/** The word row wrapped in a dummy one-subject chapter path (word last). */
		fun toEntry(): TestEntry {
			val word = DbItem(
				id = id,
				name = "word-$id",
				description = "",
				type = ItemKind.WORD,
				passedTests = successes,
				failedTests = fails,
			)
			return TestEntry(word, listOf(subject, word))
		}

		companion object {

			/** The shared dummy root every test path starts with. */
			private val subject = DbItem(name = "subject", description = "", type = ItemKind.CHAPTER)
		}
	}

	/**
	 * Runs the clever selection over the given items.
	 *
	 * @return the picked sources, in test order
	 */
	private fun select(items: List<Item>, amount: Int): List<TestEntry> {
		TestEngine.isClever = true
		val test = TestEngine()
		test.setTested(amount, null, 1, items.map { it.toEntry() })
		return test.getTestSrc()
	}

	private fun contains(picked: List<TestEntry>, item: Item): Boolean =
		picked.any { it.word.id == item.id }

	/**
	 * Regression test for the old pack-based heuristic, which could stop filling and
	 * return fewer words than requested although many more sources were available
	 * (e.g. packs of sizes 3+2+2+1+1 ended the test with 9 instead of 10 words).
	 */
	@Test
	fun exactAmountWhenSourcesPlentiful() {
		val all = ArrayList<Item>()
		var id = 0
		repeat(3) { all.add(Item(id++, 0, 1)) }   // urgency 4
		all.add(Item(id++, 0, 2))                   // urgency 3
		all.add(Item(id++, 0, 3))                   // urgency 2
		repeat(2) { all.add(Item(id++, 1, 3)) }   // urgency -1
		repeat(2) { all.add(Item(id++, 1, 4)) }   // urgency -1
		repeat(21) { all.add(Item(id++, 1, 0)) } // mastered filler
		for (amount in 1..25)
			assertEquals("wrong size for amount $amount", amount, select(all, amount).size)
		// clamping: too big or "all" amounts must use everything
		assertEquals(all.size, select(all, 999).size)
		assertEquals(all.size, select(all, -1).size)
	}

	/**
	 * When slots are scarce, untested and failing words must be preferred over
	 * mastered ones.
	 */
	@Test
	fun neediestWordsArePreferred() {
		val fresh = Item(0, 0, 0)      // urgency 5 (untested)
		val failing = Item(1, 0, 3)    // urgency 2 (0% ratio)
		val mastered = Item(2, 1, 0)   // urgency -1 (100% ratio)
		val picked = select(listOf(fresh, failing, mastered), 2)
		assertEquals(2, picked.size)
		assertTrue(contains(picked, fresh))
		assertTrue(contains(picked, failing))
		assertFalse(contains(picked, mastered))
	}

	/**
	 * Anti-starvation: a word answered correctly on the first try (100% success rate)
	 * must eventually resurface as other words pile up more tests. Simulates repeated
	 * test runs, updating stats after each run.
	 */
	@Test
	fun masteredWordsResurface() {
		val mastered = Item(0, 1, 0)
		val all = ArrayList<Item>()
		all.add(mastered)
		repeat(9) { all.add(Item(it + 1, 1, 1)) } // hovering around 50%
		var resurfaced = false
		var flip = false
		for (round in 0 until 100) {
			for (sp in select(all, 3)) {
				val hit = all.firstOrNull { it.id == sp.word.id } ?: continue
				if (hit === mastered) {
					resurfaced = true
					mastered.successes++
				} else {
					// keep flawed words around 50% so they keep competing
					flip = !flip
					if (flip) hit.successes++ else hit.fails++
				}
			}
			if (resurfaced) break
		}
		assertTrue("word nailed the first time never reappeared", resurfaced)
	}

	/**
	 * Equally urgent words should take turns rather than always picking the same
	 * prefix of the source.
	 */
	@Test
	fun equallyUrgentWordsTakeTurns() {
		val all = ArrayList<Item>()
		repeat(10) { all.add(Item(it, 1, 0)) }
		var varied = false
		val first = select(all, 2)
		var rounds = 0
		while (rounds < 50 && !varied) {
			val next = select(all, 2)
			varied = next[0].word.id != first[0].word.id || next[1].word.id != first[1].word.id
			rounds++
		}
		assertTrue("selection of equally urgent words never varies", varied)
	}

	/**
	 * The reversed direction shows the tested word and accepts one of its
	 * translations, the default direction keeps accepting the word's own name.
	 * Both compare through the `/()` variant forms.
	 */
	@Test
	fun reversedAnswerNamesATranslation() {
		val word = DbItem(id = 1, name = "house", description = "", type = ItemKind.WORD)
		val translation = DbItem(id = 2, name = "dům/domov", description = "", type = ItemKind.TRANSLATION)
		val translations = listOf(translation)

		TestEngine.isReversed = true
		try {
			assertTrue(TestEngine.matchesAnswer(word, translations, "domov"))
			assertFalse(
				"the word itself does not answer the reversed test",
				TestEngine.matchesAnswer(word, translations, "house")
			)
		} finally {
			TestEngine.isReversed = false
		}

		assertTrue(TestEngine.matchesAnswer(word, translations, "house"))
		assertFalse(
			"a translation does not answer the default direction",
			TestEngine.matchesAnswer(word, translations, "dům")
		)
	}

	/**
	 * A correct answer credits only the translation it named in a reversed test;
	 * the other translations stay untouched (they are failed only when the answer
	 * names no correct translation at all). The normal direction shows all
	 * translations, so a correct word answer credits every one of them.
	 */
	@Test
	fun reversedAnswerCreditsOnlyTheNamedTranslation() {
		val dum = DbItem(id = 1, name = "dům/domov", description = "", type = ItemKind.TRANSLATION)
		val stavba = DbItem(id = 2, name = "stavba", description = "", type = ItemKind.TRANSLATION)
		val translations = listOf(dum, stavba)

		TestEngine.isReversed = true
		try {
			assertEquals(listOf(dum), TestEngine.creditedTranslations(translations, "dům"))
			// a `/()` variant of a translation counts as naming it
			assertEquals(listOf(dum), TestEngine.creditedTranslations(translations, "domov"))
			assertEquals(listOf(stavba), TestEngine.creditedTranslations(translations, "stavba"))
			// an answer naming no translation credits none (the engine fails all)
			assertEquals(emptyList<DbItem>(), TestEngine.creditedTranslations(translations, "house"))
		} finally {
			TestEngine.isReversed = false
		}

		// the normal direction: the translations were the shown prompt, all credited
		assertEquals(translations, TestEngine.creditedTranslations(translations, "house"))
	}
}
