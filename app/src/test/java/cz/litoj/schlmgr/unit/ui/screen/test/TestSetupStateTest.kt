package cz.litoj.schlmgr.unit.ui.screen.test

import androidx.room.Room
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.screen.test.toSourceRowUi
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestSetupStateTest {

	private val testSetup get() = TestSetupState.shared

	private lateinit var db: ItemDatabase
	private lateinit var settings: Settings

	/** Process-wide state: every test leaves it empty, failure included. */
	@After
	fun resetState() {
		testSetup.reset()
		testSetup.childrenLookup = ItemRepository::children
		db.close()
		ItemRepository.resetForTest()
	}

	/** The doors refresh the counts, which read the database. */
	@Before
	fun fakeTree() {
		settings = Settings(RuntimeEnvironment.getApplication())
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		testSetup.childrenLookup = { emptyList() }
	}

	private fun item(id: Int, name: String = "i$id") =
		DbItem(id = id, name = name, description = "", type = ItemKind.CHAPTER)

	@Test
	fun `an emptied field falls back to the engine setting at the start`() {
		testSetup.setAmount("5")
		testSetup.setTime("1.5")

		testSetup.setAmount("0")
		testSetup.setTime("")

		// committed as typed, and the field can be emptied and refilled
		assertEquals("0", testSetup.amountText)
		assertEquals("", testSetup.timeText)
		// the fresh-entry reset re-reads the settings of the engine
		testSetup.reset()
		assertEquals(TestEngine.amount.toString(), testSetup.amountText)
		assertEquals(TestEngine.getSecondsPerChar().toString(), testSetup.timeText)
	}

	@Test
	fun `the fresh-entry reset re-reads the saved direction`() {
		TestEngine.applyReversed(true)
		try {
			testSetup.setReverse(false)
			testSetup.reset()
			assertTrue("the reset takes the engine direction", testSetup.reverseTest)
		} finally {
			TestEngine.applyReversed(false)
		}
	}

	@Test
	fun `the focus-lost doors restore an unusable field, keeping a valid one`() {
		val model = TestSetupViewModel(
			settings = settings, userMessages = UserMessages(RuntimeEnvironment.getApplication()))
		testSetup.setAmount("0")
		testSetup.setTime("")

		model.restoreAmount()
		model.restoreTime()

		assertEquals(TestEngine.amount.toString(), testSetup.amountText)
		assertEquals(TestEngine.getSecondsPerChar().toString(), testSetup.timeText)

		testSetup.setAmount("7")
		testSetup.setTime("0.8")
		model.restoreAmount()
		model.restoreTime()
		assertEquals("7", testSetup.amountText)
		assertEquals("0.8", testSetup.timeText)
	}

	@Test
	fun `a written separator stands for a dot and a zero restores the setting`() {
		testSetup.setTime("2,5")
		assertEquals("2.5", testSetup.timeText)

		testSetup.setTime("0")
		TestSetupViewModel(
			settings = settings, userMessages = UserMessages(RuntimeEnvironment.getApplication())).restoreTime()
		assertEquals(TestEngine.getSecondsPerChar().toString(), testSetup.timeText)
	}

	@Test
	fun `within matches a chain prefix by row id`() {
		val a = item(1)
		val b = item(2)
		assertTrue(testSetup.within(listOf(a), listOf(a, b)))
		assertTrue(testSetup.within(listOf(a, b), listOf(a, b)))
		assertTrue(testSetup.within(emptyList(), listOf(a)))
	}

	@Test
	fun `within rejects a longer prefix and a diverging id`() {
		val a = item(1)
		val b = item(2)
		assertFalse(testSetup.within(listOf(a, b), listOf(a)))
		assertFalse(testSetup.within(listOf(item(9)), listOf(a, b)))
		assertFalse(testSetup.within(listOf(b), listOf(a, b)))
	}

	@Test
	fun `dedup collapses the same chain picked twice`() {
		val chain = listOf(item(1), item(2))
		val list = arrayListOf(chain, listOf(item(1), item(2)))

		testSetup.dedup(list)

		assertEquals(1, list.size)
		assertEquals(chain, list.single())
	}

	@Test
	fun `dedup drops a chain covered by its ancestor whatever the order`() {
		val ancestor = listOf(item(1))
		val covered = listOf(item(1), item(2))

		val listed = arrayListOf(ancestor, covered)
		testSetup.dedup(listed)
		assertEquals(listOf(ancestor), listed)

		val reversed = arrayListOf(covered, ancestor)
		testSetup.dedup(reversed)
		assertEquals(listOf(ancestor), reversed)
	}

	@Test
	fun `dedup keeps disjoint chains and short lists`() {
		val disjoint = arrayListOf(listOf(item(1)), listOf(item(2)))
		testSetup.dedup(disjoint)
		assertEquals(2, disjoint.size)

		val single = arrayListOf(listOf(item(1)))
		testSetup.dedup(single)
		assertEquals(1, single.size)

		val empty = arrayListOf<List<DbItem>>()
		testSetup.dedup(empty)
		assertTrue(empty.isEmpty())
	}

	@Test
	fun `chainOf returns a search row's carried chain with its hit`() {
		val root = item(1, "subject")
		val parent = item(2, "chapter")
		val hit = item(3, "word")
		val row = ItemRow.ofSearchHit(hit, listOf(root, parent), 1, emptyList())

		assertEquals(listOf(root, parent, hit), testSetup.chainOfSeed(testSetup.seedOf(row)))
	}

	@Test
	fun `chainOf returns a parentless browse row standing alone`() {
		val subject = item(1, "subject")
		val row = ItemRow(subject, null, 1)

		assertEquals(listOf(subject), testSetup.chainOfSeed(testSetup.seedOf(row)))
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
	fun `addPicked prepends each newly picked chain`() {
		val subject = item(1, "subject")
		val first = ItemRow.ofSearchHit(item(2, "chapter"), listOf(subject), 1, emptyList())
		val second = ItemRow.ofSearchHit(item(3, "note"), listOf(subject), 2, emptyList())

		testSetup.addPicked(listOf(testSetup.seedOf(first)))
		testSetup.addPicked(listOf(testSetup.seedOf(second)))

		assertEquals(listOf(subject, item(3, "note")), testSetup.list[0])
		assertEquals(listOf(subject, item(2, "chapter")), testSetup.list[1])
		assertEquals(2, testSetup.list.size)

		testSetup.removeSource(testSetup.list[0])
		assertEquals(1, testSetup.list.size)
		testSetup.reset()
	}

	@Test
	fun `addPicked refuses rows of another subject whole`() {
		testSetup.reset()
		val subject = item(1, "subject")
		testSetup.addPicked(listOf(testSetup.seedOf(ItemRow.ofSearchHit(item(2, "chapter"), listOf(subject), 1, emptyList()))))
		val foreign = ItemRow.ofSearchHit(item(4, "word"), listOf(item(3, "other")), 1, emptyList())

		val refused = testSetup.addPicked(listOf(testSetup.seedOf(foreign)))

		assertEquals(listOf(foreign), refused)
		// the whole pick is refused, so the kept sources stay unchanged
		assertEquals(1, testSetup.list.size)
		testSetup.reset()
	}

	@Test
	fun `addPicked refuses a mixed first pick entirely`() {
		testSetup.reset()
		val a = ItemRow.ofSearchHit(item(2, "chapter"), listOf(item(1, "a")), 1, emptyList())
		val b = ItemRow.ofSearchHit(item(4, "word"), listOf(item(3, "b")), 1, emptyList())

		val refused = testSetup.addPicked(listOf(a, b).map(testSetup::seedOf))

		assertFalse(refused.isEmpty())
		assertFalse(testSetup.hasSources())
	}

	private fun pick(vararg chains: List<DbItem>) {
		testSetup.addPicked(chains.map {
			testSetup.seedOf(ItemRow.ofSearchHit(it.last(), it.dropLast(1), 1, emptyList()))
		})
	}

	@Test
	fun `removeSource returns the index and a repeated tap finds nothing`() {
		val subject = item(1, "subject")
		val first = listOf(subject, item(2))
		val second = listOf(subject, item(3))
		pick(first, second)

		assertEquals(1, testSetup.removeSource(second))

		assertEquals(null, testSetup.removeSource(second))
		assertEquals(listOf(first), testSetup.list)
	}

	@Test
	fun `clearSources empties the list, its counts and its pool`() {
		val subject = item(1, "subject")
		val chapter = item(2, "chapter")
		val word = DbItem(id = 3, name = "w", description = "", type = ItemKind.WORD)
		testSetup.childrenLookup = { id ->
			when (id) {
				1 -> listOf(chapter)
				2 -> listOf(word)
				else -> emptyList()
			}
		}
		pick(listOf(subject, chapter))
		assertEquals(1, testSetup.poolCount)
		assertEquals(1, testSetup.sourceCounts.size)

		testSetup.clearSources()

		assertFalse(testSetup.hasSources())
		assertEquals(0, testSetup.poolCount)
		assertTrue(testSetup.sourceCounts.isEmpty())
	}

	@Test
	fun `restoreSource puts the chain back at its index`() {
		val subject = item(1, "subject")
		val first = listOf(subject, item(2))
		val middle = listOf(subject, item(3))
		val last = listOf(subject, item(4))
		pick(first, middle, last)
		val index = testSetup.removeSource(middle)!!

		assertTrue(testSetup.restoreSource(middle, index))

		assertEquals(listOf(first, middle, last), testSetup.list)
	}

	@Test
	fun `restoreSource caps the index of a list that shrank`() {
		val subject = item(1, "subject")
		val first = listOf(subject, item(2))
		val last = listOf(subject, item(3))
		pick(first, last)
		val index = testSetup.removeSource(last)!!
		testSetup.removeSource(first)

		assertTrue(testSetup.restoreSource(last, index))

		assertEquals(listOf(last), testSetup.list)
	}

	@Test
	fun `restoreSource refuses a chain of another subject`() {
		val removed = listOf(item(1, "a"), item(2))
		pick(removed)
		val index = testSetup.removeSource(removed)!!
		val other = listOf(item(3, "b"), item(4))
		pick(other)

		assertFalse(testSetup.restoreSource(removed, index))

		assertEquals(listOf(other), testSetup.list)
	}

	@Test
	fun `restoreSource dedups against a pick made since the removal`() {
		val subject = item(1, "subject")
		val word = listOf(subject, item(2), item(3))
		pick(word)
		val index = testSetup.removeSource(word)!!
		val chapter = listOf(subject, item(2))
		pick(chapter)

		testSetup.restoreSource(word, index)

		assertEquals(listOf(chapter), testSetup.list)
	}

	@Test
	fun `hierarchyPicked reports a whole subject source`() {
		assertFalse(testSetup.hierarchyPicked())
		val subject = item(1, "subject")
		testSetup.addPicked(listOf(testSetup.seedOf(ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))))
		assertTrue(testSetup.hierarchyPicked())
		testSetup.reset()
	}

	@Test
	fun `the count refresh counts the pool deduped and each source alone`() {
		val subject = item(1, "subject")
		val chapter = item(2, "chapter")
		val word3 = DbItem(id = 3, name = "w3", description = "", type = ItemKind.WORD)
		val word4 = DbItem(id = 4, name = "w4", description = "", type = ItemKind.WORD)
		val word9 = DbItem(id = 9, name = "w9", description = "", type = ItemKind.WORD)
		testSetup.childrenLookup = { id ->
			when (id) {
				1 -> listOf(chapter, word9)
				2 -> listOf(word3, word4)
				else -> emptyList()
			}
		}
		val chapterSource = ItemRow.ofSearchHit(chapter, listOf(subject), 1, emptyList())
		val wordSource = ItemRow.ofSearchHit(word9, listOf(subject), 1, emptyList())

		testSetup.addPicked(listOf(chapterSource, wordSource).map(testSetup::seedOf))

		// the chapter holds two words and the word source one, with none shared
		assertEquals(3, testSetup.poolCount)
		assertEquals(2, testSetup.sourceCounts[listOf(subject, chapter)])
		assertEquals(1, testSetup.sourceCounts[listOf(subject, word9)])
	}

	@Test
	fun `a typed rate limit recounts the pool to the matching words`() {
		val subject = item(1, "subject")
		val failed = DbItem(id = 3, name = "w3", description = "", type = ItemKind.WORD, failedTests = 2)
		val untested = DbItem(id = 4, name = "w4", description = "", type = ItemKind.WORD)
		val known = DbItem(id = 5, name = "w5", description = "", type = ItemKind.WORD, passedTests = 2)
		testSetup.childrenLookup = { id -> if (id == 1) listOf(failed, untested, known) else emptyList() }
		testSetup.addPicked(listOf(testSetup.seedOf(
			ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))))
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings,
			userMessages = UserMessages(RuntimeEnvironment.getApplication()))
		try {
			model.setMaxRate("0")
			assertEquals("the failed and the untested word match", 2, testSetup.poolCount)

			model.setMaxRate("100")
			assertEquals(3, testSetup.poolCount)
		} finally {
			TestEngine.applyMaxRate(100)
		}
	}

	@Test
	fun `a rate limit recounts the captured entries both ways`() {
		val subject = item(1, "subject")
		val failed = DbItem(id = 3, name = "w3", description = "", type = ItemKind.WORD, failedTests = 2)
		val known = DbItem(id = 5, name = "w5", description = "", type = ItemKind.WORD, passedTests = 2)
		val chain = listOf(subject)
		val entries = mapOf(chain to listOf(TestEntry(failed, chain + failed), TestEntry(known, chain + known)))
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings,
			userMessages = UserMessages(RuntimeEnvironment.getApplication()))
		try {
			model.setMaxRate("0")
			testSetup.addPicked(listOf(testSetup.seedOf(
				ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))), entries = entries)
			assertEquals(1, testSetup.poolCount)

			model.setMaxRate("100")
			assertEquals("a raised limit brings back the cached words", 2, testSetup.poolCount)
		} finally {
			TestEngine.applyMaxRate(100)
		}
	}

	@Test
	fun `a fresh setup opens the rate limit at 100`() {
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings,
			userMessages = UserMessages(RuntimeEnvironment.getApplication()))
		model.setMaxRate("0")

		testSetup.reset()

		assertEquals(100, TestEngine.maxRate)
		assertEquals("100", testSetup.maxRateText)
	}

	@Test
	fun `an unusable rate limit keeps the last one and restores the field`() {
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings,
			userMessages = UserMessages(RuntimeEnvironment.getApplication()))
		try {
			model.setMaxRate("40")
			model.setMaxRate("400")

			assertEquals(40, TestEngine.maxRate)
			model.restoreMaxRate()
			assertEquals("40", testSetup.maxRateText)
		} finally {
			TestEngine.applyMaxRate(100)
		}
	}

	@Test
	fun `the caption time follows the typed multiplier and the picked pool`() {
		testSetup.childrenLookup = { emptyList() }
		val subject = item(1, "subject")
		val first = DbItem(id = 2, name = "plain", description = "", type = ItemKind.WORD)
		val second = DbItem(id = 3, name = "other", description = "", type = ItemKind.WORD)
		testSetup.addPicked(listOf(
			testSetup.seedOf(ItemRow.ofSearchHit(first, listOf(subject), 1, emptyList()))))
		testSetup.setAmount("1")
		testSetup.setTime("2")
		val one = TestEntry(first, listOf(subject, first))

		// the seconds are the engine's, so the caption only shows the typed multiplier
		assertEquals("the typed multiplier times the picked word",
			TestEngine.testTime(2f, listOf(one)), testSetup.timeSeconds())

		// an empty field times with the seconds-per-character setting of the engine
		testSetup.setTime("")
		assertEquals("an empty field falls back to the engine setting",
			TestEngine.testTime(TestEngine.getSecondsPerChar(), listOf(one)), testSetup.timeSeconds())

		// the caption times the pool the picker holds, not only the first pick
		testSetup.addPicked(listOf(
			testSetup.seedOf(ItemRow.ofSearchHit(second, listOf(subject), 2, emptyList()))))
		testSetup.setAmount("2")
		testSetup.setTime("2")
		assertEquals("the caption times every picked word",
			TestEngine.testTime(2f, listOf(TestEntry(second, listOf(subject, second)), one)),
			testSetup.timeSeconds())
	}

	@Test
	fun `captured translations time a reversed source and survive clear with undo`() {
		val subject = item(1, "subject")
		val word = DbItem(id = 2, name = "w", description = "", type = ItemKind.WORD)
		val chain = listOf(subject, word)
		val translation = DbItem(id = 3, name = "long translation", description = "", type = ItemKind.TRANSLATION)
		val entry = TestEntry(word, chain, listOf(translation))
		val reversed = TestEngine.isReversed
		TestEngine.applyReversed(true)
		try {
			testSetup.addPicked(
				listOf(testSetup.seedOf(ItemRow.ofSearchHit(word, listOf(subject), 1, emptyList()))),
				entries = mapOf(chain to listOf(entry)),
			)
			testSetup.setAmount("1")
			testSetup.setTime("2")
			val expected = TestEngine.testTime(2f, listOf(entry))
			assertEquals(expected, testSetup.timeSeconds())

			testSetup.clearSources()
			assertTrue(testSetup.restoreSource(chain, 0))
			assertEquals(expected, testSetup.timeSeconds())

			testSetup.childrenLookup = null
			testSetup.reset()
			assertFalse(testSetup.restoreSource(chain, 0))
		} finally {
			TestEngine.applyReversed(reversed)
		}
	}

	@Test
	fun `source removal subtracts only words uncovered by remaining sources`() {
		val subject = DbItem(id = 1, name = "subject", description = "", type = ItemKind.CHAPTER)
		val first = DbItem(id = 2, name = "first", description = "", type = ItemKind.CHAPTER)
		val second = DbItem(id = 3, name = "second", description = "", type = ItemKind.CHAPTER)
		val one = DbItem(id = 4, name = "one", description = "", type = ItemKind.WORD)
		val shared = DbItem(id = 5, name = "shared", description = "", type = ItemKind.WORD)
		testSetup.childrenLookup = { id ->
			when (id) {
				1 -> listOf(first, second)
				2 -> listOf(one, shared)
				3 -> listOf(shared)
				else -> emptyList()
			}
		}
		val chains = listOf(first, second)
			.map { ItemRow.ofSearchHit(it, listOf(subject), 1, emptyList()) }
			.map(testSetup::seedOf)
		testSetup.addPicked(chains)

		assertEquals(2, testSetup.poolCount)
		testSetup.childrenLookup = { id -> throw AssertionError("removal looked up $id") }
		testSetup.removeSource(listOf(subject, first))
		assertEquals("a shared word remains while the unique word leaves",
			1, testSetup.poolCount)
		assertEquals(1, testSetup.sourceCounts[listOf(subject, second)])
		testSetup.removeSource(listOf(subject, second))
		assertEquals(0, testSetup.poolCount)
	}

	@Test
	fun `a removed source leaves the pool that times the caption`() {
		val subject = DbItem(id = 1, name = "subject", description = "", type = ItemKind.CHAPTER)
		val first = DbItem(id = 2, name = "first", description = "", type = ItemKind.CHAPTER)
		val second = DbItem(id = 3, name = "second", description = "", type = ItemKind.CHAPTER)
		val one = DbItem(id = 4, name = "one", description = "", type = ItemKind.WORD)
		val shared = DbItem(id = 5, name = "shared", description = "", type = ItemKind.WORD)
		testSetup.childrenLookup = { id ->
			when (id) {
				1 -> listOf(first, second)
				2 -> listOf(one, shared)
				3 -> listOf(shared)
				else -> emptyList()
			}
		}
		testSetup.addPicked(listOf(first, second)
			.map { ItemRow.ofSearchHit(it, listOf(subject), 1, emptyList()) }
			.map(testSetup::seedOf))
		testSetup.setAmount("1")
		testSetup.setTime("2")

		testSetup.removeSource(listOf(subject, first))
		val afterRemoval = testSetup.timeSeconds()

		// the same setup picked fresh holds only the remaining words
		testSetup.reset()
		testSetup.addPicked(listOf(ItemRow.ofSearchHit(second, listOf(subject), 1, emptyList()))
			.map(testSetup::seedOf))
		testSetup.setAmount("1")
		testSetup.setTime("2")
		assertEquals("the pool drops the removed words", testSetup.timeSeconds(), afterRemoval)
	}

	@Test
	fun `a direct word removal keeps its copy in a remaining chapter`() {
		val subject = DbItem(id = 1, name = "subject", description = "", type = ItemKind.CHAPTER)
		val chapter = DbItem(id = 2, name = "chapter", description = "", type = ItemKind.CHAPTER)
		val word = DbItem(id = 3, name = "word", description = "", type = ItemKind.WORD)
		testSetup.childrenLookup = { id ->
			when (id) {
				1 -> listOf(chapter)
				2 -> listOf(word)
				else -> emptyList()
			}
		}
		val directSource = ItemRow.ofSearchHit(word, listOf(subject), 1, emptyList())
		val chapterSource = ItemRow.ofSearchHit(chapter, listOf(subject), 1, emptyList())
		testSetup.addPicked(listOf(directSource, chapterSource).map(testSetup::seedOf))

		assertEquals(1, testSetup.poolCount)
		testSetup.removeSource(listOf(subject, word))
		assertEquals("chapter", testSetup.list.single().last().name)
		assertEquals(1, testSetup.poolCount)
	}

	@Test
	fun `source mutations serialize their database walks`() {
		val subject = item(1, "subject")
		val firstLookup = CountDownLatch(1)
		val secondLookup = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		testSetup.childrenLookup = {
			when (Thread.currentThread().name) {
				"setup-first" -> {
					firstLookup.countDown()
					releaseFirst.await(5, TimeUnit.SECONDS)
				}
				"setup-second" -> secondLookup.countDown()
			}
			emptyList()
		}
		val seed = testSetup.seedOf(ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))
		val first = thread(name = "setup-first", isDaemon = true) {
			testSetup.addPicked(listOf(seed))
		}
		assertTrue(firstLookup.await(5, TimeUnit.SECONDS))
		val second = thread(name = "setup-second", isDaemon = true) {
			testSetup.addPicked(listOf(seed))
		}

		assertFalse("a second source mutation entered its lookup concurrently",
			secondLookup.await(100, TimeUnit.MILLISECONDS))
		releaseFirst.countDown()
		listOf(first, second).forEach {
			it.join(5_000)
			assertFalse("the source mutation did not finish", it.isAlive)
		}
	}
}
