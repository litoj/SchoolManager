package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests of the subject-scoped paste rules — a row refuses to
 * leave its subject ([ItemRepository.move]), and a whole subject merged
 * into another subject ([ItemRepository.mergeSubject]) runs the import
 * merge rule — the subject becomes a chapter of the target, same-named
 * chapters/notes merge below it, words merge by name in the subject
 * scope, reference links follow their rows, and the source subject
 * disappears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectMergeTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun noDesc() = null as String?

	@Test
	fun `a row refuses to move into another subject`() {
		val a = ItemRepository.createSubject("A", null)
		val b = ItemRepository.createSubject("B", null)
		val chA = ItemRepository.createChapter(a.id, "ch", null, 0)
		val chB = ItemRepository.createChapter(b.id, "ch", null, 0)
		val word = ItemRepository.createWord(chA.id, "w", null, listOf("t" to noDesc()))

		val moved = ItemRepository.move(word.id, chA.id, chB.id)

		assertEquals(ItemRepository.MoveResult.REFUSED_OTHER_SUBJECT, moved)
		assertEquals("the row stayed in its subject", listOf("w"),
			ItemRepository.children(chA.id).map { it.name })
		assertTrue(ItemRepository.children(chB.id).isEmpty())
	}

	@Test
	fun `a row moves between containers of one subject`() {
		val a = ItemRepository.createSubject("A", null)
		val ch1 = ItemRepository.createChapter(a.id, "ch1", null, 0)
		val ch2 = ItemRepository.createChapter(a.id, "ch2", null, 1)
		val word = ItemRepository.createWord(ch1.id, "w", null, listOf("t" to noDesc()))

		val moved = ItemRepository.move(word.id, ch1.id, ch2.id)

		assertEquals(ItemRepository.MoveResult.MOVED, moved)
		assertEquals(listOf("w"), ItemRepository.children(ch2.id).map { it.name })
	}

	@Test
	fun `a merged subject becomes a chapter and its duplications merge`() {
		val b = ItemRepository.createSubject("B", null)
		val kb = ItemRepository.createChapter(b.id, "kb", null, 0)
		val sameB = ItemRepository.createWord(kb.id, "same", "B side", listOf("tb" to noDesc()))
		val a = ItemRepository.createSubject("A", null)
		val ka = ItemRepository.createChapter(a.id, "ka", null, 0)
		ItemRepository.createWord(ka.id, "same", "A side", listOf("ta" to noDesc()))
		ItemRepository.createWord(ka.id, "uniq", null, listOf("tu" to noDesc()))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		// the subject became a chapter of B — B's own chapter stays
		assertEquals(listOf("kb", "A"), ItemRepository.children(b.id).map { it.name })
		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		assertEquals(listOf("ka"), ItemRepository.children(mergedSubject.id).map { it.name })
		val mergedKa = ItemRepository.children(mergedSubject.id).single()
		assertEquals(listOf("same", "uniq"), ItemRepository.children(mergedKa.id).map { it.name })
		// the same-named word merged into B's row — one row, joined description,
		// both translations, a link from the moved chapter
		val same = ItemRepository.children(mergedKa.id).single { it.name == "same" }
		assertEquals(sameB.id, same.id)
		assertTrue(same.description.contains("B side"))
		assertTrue(same.description.contains("A side"))
		assertEquals(setOf("ta", "tb"), ItemRepository.translations(same.id).map { it.name }.toSet())
		// the source subject is gone
		assertEquals(listOf("B"), ItemRepository.subjects().map { it.name })
	}

	@Test
	fun `a subject merges under a chapter of another subject`() {
		val b = ItemRepository.createSubject("B", null)
		val kb = ItemRepository.createChapter(b.id, "kb", null, 0)
		val a = ItemRepository.createSubject("A", null)

		assertTrue(ItemRepository.mergeSubject(a.id, kb.id))

		assertEquals(listOf("A"), ItemRepository.children(kb.id).map { it.name })
		assertEquals(listOf("B"), ItemRepository.subjects().map { it.name })
	}

	@Test
	fun `a reference inside the moved subject follows the merged rows`() {
		val b = ItemRepository.createSubject("B", null)
		val a = ItemRepository.createSubject("A", null)
		val ch1 = ItemRepository.createChapter(a.id, "ch1", null, 0)
		val ch2 = ItemRepository.createChapter(a.id, "ch2", null, 1)
		val word = ItemRepository.createWord(ch1.id, "w", null, listOf("t" to noDesc()))
		assertTrue(ItemRepository.reference(word.id, ch2.id))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		val mergedCh1 = ItemRepository.children(mergedSubject.id).single { it.name == "ch1" }
		val mergedCh2 = ItemRepository.children(mergedSubject.id).single { it.name == "ch2" }
		val mergedWord = ItemRepository.children(mergedCh1.id).single()
		// the reference became a reference to the merged row, not a plain copy
		assertTrue(ItemRepository.linkIsReference(mergedCh2.id, mergedWord.id))
		assertEquals(listOf(mergedWord.id), ItemRepository.children(mergedCh2.id).map { it.id })
	}

	@Test
	fun `a reference out of the moved subject keeps its outside target`() {
		val b = ItemRepository.createSubject("B", null)
		val kb = ItemRepository.createChapter(b.id, "kb", null, 0)
		val outside = ItemRepository.createWord(kb.id, "outside", null, listOf("t" to noDesc()))
		val a = ItemRepository.createSubject("A", null)
		val ch1 = ItemRepository.createChapter(a.id, "ch1", null, 0)
		assertTrue(ItemRepository.reference(outside.id, ch1.id))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		val mergedCh1 = ItemRepository.children(mergedSubject.id).single()
		// the moved chapter still references the original outside row
		assertTrue(ItemRepository.linkIsReference(mergedCh1.id, outside.id))
		assertEquals(listOf(outside.id), ItemRepository.children(kb.id).map { it.id })
	}

	@Test
	fun `an outside parent's reference re-targets the merged child`() {
		val b = ItemRepository.createSubject("B", null)
		val c = ItemRepository.createSubject("C", null)
		val a = ItemRepository.createSubject("A", null)
		val chA = ItemRepository.createChapter(a.id, "chA", null, 0)
		val word = ItemRepository.createWord(chA.id, "w", null, listOf("t" to noDesc()))
		// the moved subject's chapter references a chapter of a THIRD
		// subject, which references the moved word — the outside parent
		// never enters the plain-tree stream, and the import's scope
		// (the target subject) cannot see the word through it
		val chP = ItemRepository.createChapter(a.id, "chP", null, 1)
		val chR = ItemRepository.createChapter(c.id, "chR", null, 0)
		assertTrue(ItemRepository.reference(chR.id, chP.id))
		assertTrue(ItemRepository.reference(word.id, chR.id))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		// one word row serves the merged subject — the outside parent's
		// reference points at it, not at a duplicated survivor
		val mergedWord = ItemRepository.getByName("w", ItemKind.WORD).single()
		assertTrue(ItemRepository.linkIsReference(chR.id, mergedWord.id))
		// the original word row died with the merge — no duplicated survivor
		assertEquals(null, ItemRepository.get(word.id))
	}

	@Test
	fun `a merged subject's re-linked references change no holder counters`() {
		val b = ItemRepository.createSubject("B", null)
		val a = ItemRepository.createSubject("A", null)
		val chA = ItemRepository.createChapter(a.id, "chA", null, 0)
		val word = ItemRepository.createWord(chA.id, "w", null, listOf("t" to noDesc()))
		// the same subject's other chapter references the word
		val chB = ItemRepository.createChapter(a.id, "chB", null, 1)
		assertTrue(ItemRepository.reference(word.id, chB.id))
		// the answers bump the word and its plain holder — never a reference parent
		repeat(2) { ItemRepository.bumpCounters(listOf(word.id, chA.id), false) }

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		// the merged chapter's reference shows the word but never counts it
		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		val mergedChB = ItemRepository.children(mergedSubject.id).single { it.name == "chB" }
		assertEquals(0, ItemRepository.get(mergedChB.id)!!.failedTests)
		// the plain holder's derivation carries the fails
		val mergedChA = ItemRepository.children(mergedSubject.id).single { it.name == "chA" }
		assertEquals(2, ItemRepository.get(mergedChA.id)!!.failedTests)
	}

	@Test
	fun `a third subject's reference onto a moved row re-targets it`() {
		val b = ItemRepository.createSubject("B", null)
		val c = ItemRepository.createSubject("C", null)
		val a = ItemRepository.createSubject("A", null)
		val chA = ItemRepository.createChapter(a.id, "chA", null, 0)
		val word = ItemRepository.createWord(chA.id, "w", null, listOf("t" to noDesc()))
		// C's chapter references A's word — no link from A's reachable set
		// points back at it, so the child-side query is the only one that
		// sees this link
		val chT = ItemRepository.createChapter(c.id, "chT", null, 0)
		assertTrue(ItemRepository.reference(word.id, chT.id))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		// one word row serves the target subject — the third subject's
		// reference points at the merged copy
		val mergedWord = ItemRepository.getByName("w", ItemKind.WORD).single()
		assertTrue(ItemRepository.linkIsReference(chT.id, mergedWord.id))
		assertEquals(null, ItemRepository.get(word.id))
	}

	@Test
	fun `a target inside the moved subject refuses`() {
		val a = ItemRepository.createSubject("A", null)
		val ch = ItemRepository.createChapter(a.id, "ch", null, 0)
		ItemRepository.createSubject("B", null)

		assertFalse(ItemRepository.mergeSubject(a.id, ch.id))

		// nothing changed — both subjects stand, A still holds its chapter
		assertEquals(listOf("A", "B"), ItemRepository.subjects().map { it.name })
		assertEquals(listOf("ch"), ItemRepository.children(a.id).map { it.name })
	}

	@Test
	fun `a word's counters carry into the merged copy`() {
		val b = ItemRepository.createSubject("B", null)
		val a = ItemRepository.createSubject("A", null)
		val ka = ItemRepository.createChapter(a.id, "ka", null, 0)
		val word = ItemRepository.createWord(ka.id, "w", null, listOf("t" to noDesc()))
		ItemRepository.recordPass(listOf(word.id))

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		val mergedKa = ItemRepository.children(mergedSubject.id).single()
		val merged = ItemRepository.children(mergedKa.id).single()
		assertEquals(1, merged.passedTests)
	}

	@Test
	fun `a reference-only row merges with its content and keeps its reference`() {
		val b = ItemRepository.createSubject("B", null)
		val a = ItemRepository.createSubject("A", null)
		val ch1 = ItemRepository.createChapter(a.id, "ch1", null, 0)
		val ch2 = ItemRepository.createChapter(a.id, "ch2", null, 1)
		val word = ItemRepository.createWord(ch1.id, "w", null, listOf("t" to noDesc()))
		assertTrue(ItemRepository.reference(word.id, ch2.id))
		ItemRepository.remove(word.id, ch1.id)
		// the word survives on its reference parent alone — no plain parent left
		assertEquals(listOf(word.id), ItemRepository.children(ch2.id).map { it.id })

		assertTrue(ItemRepository.mergeSubject(a.id, b.id))

		val mergedSubject = ItemRepository.children(b.id).single { it.name == "A" }
		val mergedCh2 = ItemRepository.children(mergedSubject.id).single { it.name == "ch2" }
		val mergedWord = ItemRepository.children(mergedCh2.id).single()
		assertEquals("the referenced row survived the merge", "w", mergedWord.name)
		assertEquals(listOf("t"), ItemRepository.translations(mergedWord.id).map { it.name })
		assertTrue(ItemRepository.linkIsReference(mergedCh2.id, mergedWord.id))
	}
}
