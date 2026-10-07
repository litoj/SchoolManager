package cz.litoj.schlmgr.gui.engine.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import cz.litoj.schlmgr.gui.engine.objects.templates.Container;
import cz.litoj.schlmgr.gui.engine.objects.templates.TwoSided;

/**
 * Unit tests for the content-selection heuristic of {@link Test} (the "clever" mode).
 *
 * @author Josef Litoš
 */
@SuppressWarnings("rawtypes")
public class TestSelectionTest {

	/**
	 * A tested object with mutable success stats. Ratio follows
	 * {@link cz.litoj.schlmgr.gui.engine.objects.templates.BasicData#getRatio()} semantics:
	 * {@code -1} when never tested, otherwise {@code 100 * successes / tests}.
	 */
	private static final class Item {
		final TwoSided t = mock(TwoSided.class);
		final Test.SrcPath path;
		int successes, fails;

		Item(int successes, int fails) {
			this.successes = successes;
			this.fails = fails;
			when(t.getRatio()).thenAnswer(i -> ratio());
			when(t.getSFCount()).thenAnswer(i -> count());
			path = new Test.SrcPath(List.of(mock(Container.class), t));
		}

		int ratio() {
			return successes == 0 && fails == 0 ? -1 : 100 * successes / (successes + fails);
		}

		int count() {
			return successes + fails;
		}
	}

	/**
	 * Runs the clever selection over the given items.
	 *
	 * @return the picked sources, in test order
	 */
	private static List<Test.SrcPath> select(List<Item> items, int amount) {
		Test<TwoSided> test = new Test<>(TwoSided.class);
		Test.setClever(true);
		List<Test.SrcPath> src = new ArrayList<>();
		for (Item it : items) src.add(it.path);
		test.setTested(amount, null, 1, src);
		return test.getTestSrc();
	}

	private static boolean contains(List<Test.SrcPath> picked, Item it) {
		for (Test.SrcPath sp : picked) if (sp.t == it.t) return true;
		return false;
	}

	/**
	 * Regression test for the old pack-based heuristic, which could stop filling and
	 * return fewer words than requested although many more sources were available
	 * (e.g. packs of sizes 3+2+2+1+1 ended the test with 9 instead of 10 words).
	 */
	@org.junit.Test
	public void exactAmountWhenSourcesPlentiful() {
		List<Item> all = new ArrayList<>();
		for (int i = 0; i < 3; i++) all.add(new Item(0, 1));       // urgency 4
		all.add(new Item(0, 2));                                    // urgency 3
		all.add(new Item(0, 3));                                    // urgency 2
		for (int i = 0; i < 2; i++) all.add(new Item(1, 3));       // urgency -1
		for (int i = 0; i < 2; i++) all.add(new Item(1, 4));       // urgency 0
		for (int i = 0; i < 21; i++) all.add(new Item(1, 0));      // mastered filler
		for (int amount = 1; amount <= 25; amount++)
			assertEquals("wrong size for amount " + amount, amount, select(all, amount).size());
		// clamping: too big or "all" amounts must use everything
		assertEquals(all.size(), select(all, 999).size());
		assertEquals(all.size(), select(all, -1).size());
	}

	/**
	 * When slots are scarce, untested and failing words must be preferred over
	 * mastered ones.
	 */
	@org.junit.Test
	public void neediestWordsArePreferred() {
		Item fresh = new Item(0, 0);      // urgency 5 (untested)
		Item failing = new Item(0, 3);    // urgency 2 (0% ratio)
		Item mastered = new Item(1, 0);   // urgency -1 (100% ratio)
		List<Test.SrcPath> picked = select(List.of(fresh, failing, mastered), 2);
		assertEquals(2, picked.size());
		assertTrue(contains(picked, fresh));
		assertTrue(contains(picked, failing));
		assertFalse(contains(picked, mastered));
	}

	/**
	 * Anti-starvation: a word answered correctly on the first try (100% success rate)
	 * must eventually resurface as other words pile up more tests. Simulates repeated
	 * test runs, updating stats after each run.
	 */
	@org.junit.Test
	public void masteredWordsResurface() {
		Item mastered = new Item(1, 0);
		List<Item> all = new ArrayList<>();
		all.add(mastered);
		for (int i = 0; i < 9; i++) all.add(new Item(1, 1)); // hovering around 50%
		boolean resurfaced = false, flip = false;
		for (int round = 0; round < 100 && !resurfaced; round++) {
			for (Test.SrcPath sp : select(all, 3)) {
				if (sp.t == mastered.t) {
					resurfaced = true;
					mastered.successes++;
				} else for (Item it : all)
					// keep flawed words around 50% so they keep competing
					if (it.path == sp) {
						flip = !flip;
						if (flip) it.successes++;
						else it.fails++;
						break;
					}
			}
		}
		assertTrue("word nailed the first time never reappeared", resurfaced);
	}

	/**
	 * Equally urgent words should take turns rather than always picking the same
	 * prefix of the source.
	 */
	@org.junit.Test
	public void equallyUrgentWordsTakeTurns() {
		List<Item> all = new ArrayList<>();
		for (int i = 0; i < 10; i++) all.add(new Item(1, 0));
		boolean varied = false;
		List<Test.SrcPath> first = select(all, 2);
		for (int i = 0; i < 50 && !varied; i++) {
			List<Test.SrcPath> next = select(all, 2);
			varied = next.get(0) != first.get(0) || next.get(1) != first.get(1);
		}
		assertTrue("selection of equally urgent words never varies", varied);
	}
}
