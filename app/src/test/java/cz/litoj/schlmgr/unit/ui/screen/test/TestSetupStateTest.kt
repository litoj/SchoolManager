package cz.litoj.schlmgr.unit.ui.screen.test

import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.screen.test.toSourceRowUi
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class TestSetupStateTest {

	/** Process-wide state: every test leaves it empty, failure included. */
	@After
	fun resetState() {
		TestSetupState.reset()
		TestSetupState.childrenLookup = ItemRepository::children
	}

	/** The doors refresh the counts, which read the database. */
	@Before
	fun fakeTree() {
		TestSetupState.childrenLookup = { emptyList() }
	}

	private fun item(id: Int, name: String = "i$id") =
		DbItem(id = id, name = name, description = "", type = ItemKind.CHAPTER)

	@Test
	fun `an input below one falls back to the engine default at the start`() {
		TestSetupState.setAmount("5")
		TestSetupState.setTime("9")

		TestSetupState.setAmount("0")
		TestSetupState.setTime("")

		// committed as typed, and the field can be emptied and refilled
		assertEquals("0", TestSetupState.amountText)
		assertEquals("", TestSetupState.timeText)
		// the fresh-entry reset re-reads the settings of the engine
		TestSetupState.reset()
		assertEquals(TestEngine.amount.toString(), TestSetupState.amountText)
		assertEquals(TestEngine.getDefaultTime().toString(), TestSetupState.timeText)
	}

	@Test
	fun `the fresh-entry reset re-reads the saved direction`() {
		TestEngine.applyReversed(true)
		try {
			TestSetupState.setReverse(false)
			TestSetupState.reset()
			assertTrue("the reset takes the engine direction", TestSetupState.reverseTest)
		} finally {
			TestEngine.applyReversed(false)
		}
	}

	@Test
	fun `the focus-lost doors restore a below-one field, keeping a valid one`() {
		val model = TestSetupViewModel()
		TestSetupState.setAmount("0")
		TestSetupState.setTime("")

		model.restoreAmount()
		model.restoreTime()

		assertEquals(TestEngine.amount.toString(), TestSetupState.amountText)
		assertEquals(TestEngine.getDefaultTime().toString(), TestSetupState.timeText)

		TestSetupState.setAmount("7")
		model.restoreAmount()
		assertEquals("7", TestSetupState.amountText)
	}

	@Test
	fun `within matches a chain prefix by row id`() {
		val a = item(1)
		val b = item(2)
		assertTrue(TestSetupState.within(listOf(a), listOf(a, b)))
		assertTrue(TestSetupState.within(listOf(a, b), listOf(a, b)))
		assertTrue(TestSetupState.within(emptyList(), listOf(a)))
	}

	@Test
	fun `within rejects a longer prefix and a diverging id`() {
		val a = item(1)
		val b = item(2)
		assertFalse(TestSetupState.within(listOf(a, b), listOf(a)))
		assertFalse(TestSetupState.within(listOf(item(9)), listOf(a, b)))
		assertFalse(TestSetupState.within(listOf(b), listOf(a, b)))
	}

	@Test
	fun `dedup collapses the same chain picked twice`() {
		val chain = listOf(item(1), item(2))
		val list = arrayListOf(chain, listOf(item(1), item(2)))

		TestSetupState.dedup(list)

		assertEquals(1, list.size)
		assertEquals(chain, list.single())
	}

	@Test
	fun `dedup drops a chain covered by its ancestor whatever the order`() {
		val ancestor = listOf(item(1))
		val covered = listOf(item(1), item(2))

		val listed = arrayListOf(ancestor, covered)
		TestSetupState.dedup(listed)
		assertEquals(listOf(ancestor), listed)

		val reversed = arrayListOf(covered, ancestor)
		TestSetupState.dedup(reversed)
		assertEquals(listOf(ancestor), reversed)
	}

	@Test
	fun `dedup keeps disjoint chains and short lists`() {
		val disjoint = arrayListOf(listOf(item(1)), listOf(item(2)))
		TestSetupState.dedup(disjoint)
		assertEquals(2, disjoint.size)

		val single = arrayListOf(listOf(item(1)))
		TestSetupState.dedup(single)
		assertEquals(1, single.size)

		val empty = arrayListOf<List<DbItem>>()
		TestSetupState.dedup(empty)
		assertTrue(empty.isEmpty())
	}

	@Test
	fun `chainOf returns a search row's carried chain with its hit`() {
		val root = item(1, "subject")
		val parent = item(2, "chapter")
		val hit = item(3, "word")
		val row = ItemRow.ofSearchHit(hit, listOf(root, parent), 1, emptyList())

		assertEquals(listOf(root, parent, hit), TestSetupState.chainOfSeed(TestSetupState.seedOf(row)))
	}

	@Test
	fun `chainOf returns a parentless browse row standing alone`() {
		val subject = item(1, "subject")
		val row = ItemRow(subject, null, 1)

		assertEquals(listOf(subject), TestSetupState.chainOfSeed(TestSetupState.seedOf(row)))
	}

	@Test
	fun `a source row shows the kind of the chain's last item`() {
		val subject = item(1, "subject")
		val note = DbItem(id = 2, name = "n", description = "", type = ItemKind.NOTE)
		val word = DbItem(id = 3, name = "w", description = "", type = ItemKind.WORD)

		assertEquals(RowKind.NOTE, listOf(subject, note).toSourceRowUi().kind)
		assertEquals(RowKind.WORD, listOf(subject, word).toSourceRowUi().kind)
	}

	@Test
	fun `fillFromSelection replaces the sources deduped`() {
		val subject = item(1, "subject")
		val chapter = item(2, "chapter")
		// the deeper chain is covered by its ancestor, so the subject alone suffices
		val coveredWord = ItemRow.ofSearchHit(item(3, "word"), listOf(subject, chapter), 1, emptyList())
		val wholeSubject = ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList())

		TestSetupState.fillFromSelection(listOf(coveredWord, wholeSubject).map(TestSetupState::seedOf))

		assertEquals(1, TestSetupState.list.size)
		assertEquals(listOf(subject), TestSetupState.list.single())

		TestSetupState.reset()
		assertFalse(TestSetupState.hasSources())
	}

	@Test
	fun `addPicked prepends each newly picked chain`() {
		val subject = item(1, "subject")
		val first = ItemRow.ofSearchHit(item(2, "chapter"), listOf(subject), 1, emptyList())
		val second = ItemRow.ofSearchHit(item(3, "note"), listOf(subject), 2, emptyList())

		TestSetupState.addPicked(listOf(TestSetupState.seedOf(first)))
		TestSetupState.addPicked(listOf(TestSetupState.seedOf(second)))

		assertEquals(listOf(subject, item(3, "note")), TestSetupState.list[0])
		assertEquals(listOf(subject, item(2, "chapter")), TestSetupState.list[1])
		assertEquals(2, TestSetupState.list.size)

		TestSetupState.removeSource(TestSetupState.list[0])
		assertEquals(1, TestSetupState.list.size)
		TestSetupState.reset()
	}

	@Test
	fun `fillFromSelection refuses a selection spanning several subjects`() {
		TestSetupState.reset()
		val a = ItemRow.ofSearchHit(item(2, "chapter"), listOf(item(1, "a")), 1, emptyList())
		val b = ItemRow.ofSearchHit(item(4, "word"), listOf(item(3, "b")), 1, emptyList())

		val refused = TestSetupState.fillFromSelection(listOf(a, b).map(TestSetupState::seedOf))

		assertFalse(refused.isEmpty())
		assertFalse(TestSetupState.hasSources())
	}

	@Test
	fun `addPicked refuses rows of another subject whole`() {
		TestSetupState.reset()
		val subject = item(1, "subject")
		TestSetupState.addPicked(listOf(TestSetupState.seedOf(ItemRow.ofSearchHit(item(2, "chapter"), listOf(subject), 1, emptyList()))))
		val foreign = ItemRow.ofSearchHit(item(4, "word"), listOf(item(3, "other")), 1, emptyList())

		val refused = TestSetupState.addPicked(listOf(TestSetupState.seedOf(foreign)))

		assertEquals(listOf(foreign), refused)
		// the whole pick is refused, so the kept sources stay unchanged
		assertEquals(1, TestSetupState.list.size)
		TestSetupState.reset()
	}

	@Test
	fun `addPicked refuses a mixed first pick entirely`() {
		TestSetupState.reset()
		val a = ItemRow.ofSearchHit(item(2, "chapter"), listOf(item(1, "a")), 1, emptyList())
		val b = ItemRow.ofSearchHit(item(4, "word"), listOf(item(3, "b")), 1, emptyList())

		val refused = TestSetupState.addPicked(listOf(a, b).map(TestSetupState::seedOf))

		assertFalse(refused.isEmpty())
		assertFalse(TestSetupState.hasSources())
	}

	@Test
	fun `hierarchyPicked reports a whole subject source`() {
		assertFalse(TestSetupState.hierarchyPicked())
		val subject = item(1, "subject")
		TestSetupState.addPicked(listOf(TestSetupState.seedOf(ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))))
		assertTrue(TestSetupState.hierarchyPicked())
		TestSetupState.reset()
	}

	@Test
	fun `the count refresh counts the pool deduped and each source alone`() {
		val subject = item(1, "subject")
		val chapter = item(2, "chapter")
		val word3 = DbItem(id = 3, name = "w3", description = "", type = ItemKind.WORD)
		val word4 = DbItem(id = 4, name = "w4", description = "", type = ItemKind.WORD)
		val word9 = DbItem(id = 9, name = "w9", description = "", type = ItemKind.WORD)
		TestSetupState.childrenLookup = { id ->
			when (id) {
				1 -> listOf(chapter, word9)
				2 -> listOf(word3, word4)
				else -> emptyList()
			}
		}
		val chapterSource = ItemRow.ofSearchHit(chapter, listOf(subject), 1, emptyList())
		val wordSource = ItemRow.ofSearchHit(word9, listOf(subject), 1, emptyList())

		TestSetupState.fillFromSelection(listOf(chapterSource, wordSource).map(TestSetupState::seedOf))

		// the chapter holds two words and the word source one, with none shared
		assertEquals(3, TestSetupState.poolCount)
		assertEquals(2, TestSetupState.sourceCounts[listOf(subject, chapter)])
		assertEquals(1, TestSetupState.sourceCounts[listOf(subject, word9)])
	}
}
