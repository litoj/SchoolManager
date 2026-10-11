package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.KeptTranslation
import cz.litoj.schlmgr.data.db.WordEdit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ItemRepository.editWord] on a real in-memory database — the normal edit of
 * a word, the fork of a shared word that the editor renames, and the
 * reference choice the fork carries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordEditTest {

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

	private fun word(parentId: Int, name: String, vararg translations: String): DbItem =
		ItemRepository.createWord(parentId, name, null, translations.map { it to (null as String?) })

	/** The word's translation row of [name]. */
	private fun trl(wordId: Int, name: String): DbItem =
		ItemRepository.translations(wordId).first { it.name == name }

	private fun names(wordId: Int): List<String> = ItemRepository.translations(wordId).map { it.name }

	/** Every current translation of [wordId] kept as it is — the editor's untouched rows. */
	private fun keptOf(wordId: Int): List<KeptTranslation> =
		ItemRepository.translations(wordId).map { KeptTranslation(it.id, it.name, it.description) }

	private fun edit(
		wordId: Int, parentId: Int, name: String,
		kept: List<KeptTranslation> = emptyList(),
		removed: List<Int> = emptyList(),
		additions: List<Pair<String, String?>> = emptyList(),
		description: String? = null,
		referencesToFork: List<Int> = emptyList(),
	): WordEdit = WordEdit(wordId, parentId, name, description, kept, removed, additions, referencesToFork)

	/**
	 * A word linked into both chapters — the second [ItemRepository.createWord]
	 * name-merges into the first row, so one row carries two plain links.
	 */
	private fun sharedWord(c1: Int, c2: Int, name: String, vararg translations: String): DbItem {
		val first = word(c1, name, *translations)
		// the same translations — the merge adds nothing to the row
		word(c2, name, *translations)
		return first
	}

	/** A word linked into every one of [chapters] — the first createWord makes the row. */
	private fun sharedIn(chapters: List<Int>, name: String, vararg translations: String): DbItem {
		val first = word(chapters.first(), name, *translations)
		for (c in chapters.drop(1)) word(c, name, *translations)
		return first
	}

	// --------------------------------------------------------------- normal edits

	@Test
	fun `an unshared word edits in place`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes", "hound")
		val edited = ItemRepository.editWord(edit(dog.id, subject.id, "doggo", keptOf(dog.id)))

		assertEquals(dog.id, edited.id)
		assertEquals("doggo", ItemRepository.children(subject.id).single().name)
		assertEquals(listOf("pes", "hound"), names(dog.id))
	}

	@Test
	fun `an unchanged shared word edits the original for every chapter`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "doggo", keptOf(dog.id)))

		assertEquals(dog.id, edited.id)
		// one row, both chapters — the edit reached every occurrence
		assertEquals("doggo", ItemRepository.children(c1.id).single().name)
		assertEquals("doggo", ItemRepository.children(c2.id).single().name)
		assertEquals(ItemRepository.children(c1.id).single().id, ItemRepository.children(c2.id).single().id)
	}

	@Test
	fun `a rename-only edit changes the shared word in every chapter`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "kucer", ""))))

		// nothing was taken away, so the update is a normal one
		assertEquals(dog.id, edited.id)
		assertEquals(listOf("kucer"), names(dog.id))
		assertEquals(listOf("kucer"), names(ItemRepository.children(c2.id).single().id))
	}

	@Test
	fun `renaming a translation shared with another word leaves theirs alone`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "hound")
		val cat = word(c1.id, "cat", "hound")
		// one shared row serves both words
		assertEquals(trl(dog.id, "hound").id, trl(cat.id, "hound").id)

		ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "hound").id, "kucer", ""))))

		assertEquals(listOf("kucer"), names(dog.id))
		assertEquals("the other word keeps its translation", listOf("hound"), names(cat.id))
	}

	@Test
	fun `a rename-join merges the same-named translations`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "x")
		val cat = word(c1.id, "cat", "x")

		// renaming cat onto dog joins the words — both carry a translation
		// named x, and the survivor keeps one row of it
		ItemRepository.rename(cat.id, "dog")

		val joined = ItemRepository.getByName("dog", ItemKind.WORD).single()
		assertEquals(listOf("x"), names(joined.id))
	}

	@Test
	fun `an edit that would strip every translation fails`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "x")

		try {
			ItemRepository.editWord(edit(dog.id, c1.id, "dog",
				removed = listOf(trl(dog.id, "x").id)))
			fail("a word without a translation must fail the edit")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("translation"))
		}
		// the word kept its translation
		assertEquals(listOf("x"), names(dog.id))
	}

	@Test
	fun `a same-name kept row joins what an earlier rename merged in`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(c1.id, "dog", null,
			listOf("tA" to "aaa", "tE" to "eee"))
		val a = trl(word.id, "tA")
		val e = trl(word.id, "tE")

		// rename A onto E's name (descriptions join), keep E itself with
		// a new description
		val edited = ItemRepository.editWord(edit(word.id, c1.id, "dog",
			kept = listOf(
				KeptTranslation(a.id, "tE", ""),
				KeptTranslation(e.id, "tE", "new"))))

		// E's row holds all three descriptions joined — the same-name pass
		// merged into the live row, not the stale pre-edit snapshot
		assertEquals("eee\naaa\nnew", trl(edited.id, "tE").description)
	}

	@Test
	fun `an edit whose only addition is empty-named fails`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "x")

		try {
			ItemRepository.editWord(edit(dog.id, c1.id, "dog",
				removed = listOf(trl(dog.id, "x").id),
				additions = listOf("" to "d")))
			fail("an empty-named addition must not satisfy the word's translation rule")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("translation"))
		}
		// the word kept its translation
		assertEquals(listOf("x"), names(dog.id))
	}

	@Test
	fun `a rename-join keeps a reference parent a reference`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "x")
		val cat = word(c1.id, "cat", "y")
		// a chapter referencing dog — it holds no word of cat's name
		val other = ItemRepository.createChapter(subject.id, "other", null)
		assertTrue(ItemRepository.reference(dog.id, other.id))

		ItemRepository.rename(dog.id, "cat")

		val joined = ItemRepository.getByName("cat", ItemKind.WORD).single()
		// the moved links keep their kind — the reference stays one
		assertEquals(true, ItemRepository.linkIsReference(other.id, joined.id))
		assertEquals(false, ItemRepository.linkIsReference(c1.id, joined.id))
	}

	@Test
	fun `a rename-join's clashing translation adopts counters and dies unshared`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "x")
		val cat = word(c1.id, "cat", "y")
		// cat's own same-named "x" row — a pre-merge state the creator's
		// name rule never makes, with the more-tested counters
		val loser = ItemRepository.legacyInsert("x", "d", ItemKind.TRANSLATION, 5, 0)
		ItemRepository.legacyLinkAtEnd(cat.id, loser)

		ItemRepository.rename(cat.id, "dog")

		// one "x" serves the joined word, carrying the loser's counters
		val joined = ItemRepository.getByName("dog", ItemKind.WORD).single()
		assertEquals(listOf("x", "y"), names(joined.id))
		assertEquals("the unshared loser row is gone", null, ItemRepository.get(loser))
		assertEquals(5, trl(joined.id, "x").passedTests)
	}

	@Test
	fun `a fork onto an existing word appends its kept translations`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val cat = ItemRepository.createWord(c1.id, "cat", null, listOf("t1" to null, "t2" to null))
		val dog = ItemRepository.createWord(c1.id, "dog", null, listOf("a" to null, "b" to null))

		// keep a, remove b, rename onto cat — the fork joins the existing word
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "cat",
			kept = listOf(KeptTranslation(trl(dog.id, "a").id, "a", "")),
			removed = listOf(trl(dog.id, "b").id)))

		assertEquals("the fork joined the existing word", cat.id, edited.id)
		// the kept row appended after the existing ones — duplicate
		// positions would leave the order arbitrary
		assertEquals(listOf("t1", "t2", "a"), names(edited.id))
	}

	@Test
	fun `a rename-join adopts the more-tested counters`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		// the loser is the more-tested one — its counters survive
		val dog = word(c1.id, "dog", "x")
		val cat = word(c1.id, "cat", "x")
		ItemRepository.bumpCounters(listOf(cat.id), false)
		ItemRepository.bumpCounters(listOf(cat.id), false)

		ItemRepository.rename(cat.id, "dog")

		val joined = ItemRepository.getByName("dog", ItemKind.WORD).single()
		assertEquals(0, joined.passedTests)
		assertEquals(2, joined.failedTests)
	}

	@Test
	fun `an imported translation merging by name adopts the more-tested counters`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		// the subject already holds a same-named translation with fewer tests
		val cat = word(c1.id, "cat", "micka")
		ItemRepository.bumpCounters(listOf(trl(cat.id, "micka").id), false)
		ItemRepository.bumpCounters(listOf(trl(cat.id, "micka").id), false)
		ItemRepository.bumpCounters(listOf(trl(cat.id, "micka").id), false)

		// a file's word carries the same translation name with more tests
		ItemRepository.createWord(c1.id, "dog", null, listOf("pes" to null, "micka" to null))
		ItemRepository.bumpCounters(
			listOf(trl(ItemRepository.getByName("dog", ItemKind.WORD).single().id, "micka").id), true)

		val merged = trl(cat.id, "micka")
		assertEquals("one row serves both words", 3, merged.failedTests)
		assertTrue("the more-tested pair survives", merged.passedTests >= 1)
	}

	@Test
	fun `an added translation edits the shared word in place`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "dog", keptOf(dog.id),
			additions = listOf<Pair<String, String?>>("hound" to null)))

		assertEquals(dog.id, edited.id)
		assertEquals(listOf("pes", "hound"), names(dog.id))
		assertEquals(dog.id, ItemRepository.children(c2.id).single().id)
	}

	@Test
	fun `a description change edits the shared word in place`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val pes = trl(dog.id, "pes")
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(pes.id, "pes", "slovo"))))

		assertEquals(dog.id, edited.id)
		assertEquals("slovo", ItemRepository.translations(dog.id).single().description)
	}

	@Test
	fun `a removal without a rename edits the shared word in place`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		ItemRepository.recordPass(listOf(dog.id))
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		// the word keeps its name, so a fork would only add its duplicate to
		// the subject — every chapter holds the one row and sees the removal
		assertEquals(dog.id, edited.id)
		assertEquals(listOf("pes"), names(dog.id))
		assertEquals(dog.id, ItemRepository.children(c1.id).single().id)
		assertEquals(dog.id, ItemRepository.children(c2.id).single().id)
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.WORD).size)
		assertEquals(1, ItemRepository.get(dog.id)!!.passedTests)
	}

	@Test
	fun `a removed and re-added translation edits the shared word in place`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id),
			additions = listOf<Pair<String, String?>>("cat" to null)))

		// remove plus add is how the editor swaps a translation — the word
		// keeps its name, so nothing forks and every chapter sees the new set
		assertEquals(listOf("pes", "cat"), names(dog.id))
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.WORD).size)
	}

	@Test
	fun `an in-place edit reaches the chapters that reference the word`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		assertTrue(ItemRepository.reference(dog.id, c3.id))
		ItemRepository.editWord(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		// the reference keeps showing the row, so it sees the edit with it
		assertTrue(ItemRepository.linkIsReference(c3.id, dog.id))
		assertEquals(listOf("pes"), names(dog.id))
	}

	@Test
	fun `a shared word renamed onto an existing word joins it in every chapter`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val cat = word(c2.id, "cat", "micka")
		val edited = ItemRepository.editWord(edit(dog.id, c1.id, "cat", keptOf(dog.id)))

		// the plain name-join — every chapter shows the surviving row
		assertEquals(cat.id, edited.id)
		assertEquals(cat.id, ItemRepository.children(c1.id).single().id)
		assertEquals(cat.id, ItemRepository.children(c2.id).single().id)
		assertTrue(ItemRepository.getByName("dog", ItemKind.WORD).isEmpty())
	}

	@Test
	fun `an unshared removal deletes the translation`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes", "hound")
		val hound = trl(dog.id, "hound")
		ItemRepository.editWord(edit(dog.id, subject.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")), listOf(hound.id)))

		assertEquals(listOf("pes"), names(dog.id))
		assertEquals(null, ItemRepository.get(hound.id))
	}

	@Test
	fun `an unshared addition joins by name`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "hound")
		ItemRepository.editWord(edit(dog.id, subject.id, "dog", keptOf(dog.id),
			additions = listOf<Pair<String, String?>>("hound" to null)))

		// the subject's "hound" row serves the word too — no second row of one name
		assertEquals(listOf("pes", "hound"), names(dog.id))
		assertEquals(trl(dog.id, "hound").id, trl(cat.id, "hound").id)
	}

	@Test
	fun `a renamed subject-level translation leaves the other word's row alone`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes", "micka")
		val dogWord = ItemRepository.editWord(edit(dog.id, subject.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "kucer", ""))))

		// a shared row keeps serving its other words — the edited word gets
		// its own row of the new name
		assertEquals(dog.id, dogWord.id)
		assertEquals(listOf("kucer"), names(dog.id))
		assertEquals(listOf("pes", "micka"), names(cat.id))
	}

	@Test
	fun `a rename onto an existing translation joins it and keeps both descriptions`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "hound")
		ItemRepository.setDescription(trl(dog.id, "pes").id, "d1")
		ItemRepository.setDescription(trl(cat.id, "hound").id, "d2")
		ItemRepository.editWord(edit(dog.id, subject.id, "dog",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "hound", ""))))

		// no second row of one name — the deleted row's description joined the survivor
		assertEquals(listOf("hound"), names(dog.id))
		assertEquals(listOf("hound"), names(cat.id))
		assertEquals("d2\nd1", ItemRepository.translations(cat.id).single().description)
		assertTrue(ItemRepository.getByName("pes", ItemKind.TRANSLATION).isEmpty())
	}

	@Test
	fun `a word rename onto an existing word joins it and keeps both descriptions`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "micka")
		ItemRepository.setDescription(dog.id, "d1")
		ItemRepository.setDescription(cat.id, "d2")
		val edited = ItemRepository.editWord(edit(dog.id, subject.id, "cat", keptOf(dog.id)))

		// the two rows joined into the subject's "cat" word
		assertEquals(cat.id, edited.id)
		assertEquals("d2\nd1", ItemRepository.get(cat.id)!!.description)
		assertTrue(ItemRepository.getByName("dog", ItemKind.WORD).isEmpty())
		assertEquals(setOf("pes", "micka"), names(cat.id).toSet())
	}

	// ------------------------------------------------------------------- forks

	@Test
	fun `a removal with a rename forks the shared word`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		word(c1.id, "aaa", "x")
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		ItemRepository.recordPass(listOf(dog.id))
		val result = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		// the edited chapter holds the fork, in the original's slot — the other keeps the original
		assertEquals(listOf("aaa", "doggo"), ItemRepository.children(c1.id).map { it.name })
		assertTrue(result.id != dog.id)
		assertEquals(listOf("dog"), ItemRepository.children(c2.id).map { it.name })
		assertEquals(dog.id, ItemRepository.children(c2.id).single().id)
		// the rename keeps the subject's word names unique — one "doggo", one "dog"
		assertEquals(1, ItemRepository.getByName("doggo", ItemKind.WORD).size)
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.WORD).size)
		// the kept row left the original, the removed one stayed
		assertEquals(listOf("pes"), names(result.id))
		assertEquals(listOf("hound"), names(dog.id))
		// the fork starts fresh — the original keeps its counters
		assertEquals(0, result.passedTests)
		assertEquals(1, ItemRepository.get(dog.id)!!.passedTests)
	}

	@Test
	fun `a fork updates a kept translation to the editor's values`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		ItemRepository.setDescription(trl(dog.id, "pes").id, "old")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "kucer", "new")),
			listOf(trl(dog.id, "hound").id)))

		// the kept row follows the fork and takes the editor's name and value
		assertEquals(listOf("kucer"), names(fork.id))
		assertEquals("new", ItemRepository.translations(fork.id).single().description)
		assertEquals(listOf("hound"), names(dog.id))
		assertTrue(ItemRepository.getByName("pes", ItemKind.TRANSLATION).isEmpty())
	}

	@Test
	fun `a fork takes the edited name and description, the original keeps its own`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		ItemRepository.setDescription(dog.id, "old")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id), description = "new"))

		assertEquals("doggo", fork.name)
		assertEquals("new", fork.description)
		assertEquals("dog", ItemRepository.get(dog.id)!!.name)
		assertEquals("old", ItemRepository.get(dog.id)!!.description)
	}

	@Test
	fun `a fork keeps the editor's translation order`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound", "cat")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(
				KeptTranslation(trl(dog.id, "cat").id, "cat", ""),
				KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		assertEquals(listOf("cat", "pes"), names(fork.id))
	}

	@Test
	fun `a fork's added translation joins the new word`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id),
			additions = listOf<Pair<String, String?>>("cat" to null)))

		assertEquals(listOf("pes", "cat"), names(fork.id))
		assertEquals(listOf("hound"), names(dog.id))
	}

	@Test
	fun `removing every translation still forks and the original keeps them`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			removed = listOf(trl(dog.id, "pes").id, trl(dog.id, "hound").id),
			additions = listOf<Pair<String, String?>>("cat" to null)))

		assertEquals(listOf("cat"), names(fork.id))
		assertEquals(listOf("pes", "hound"), names(dog.id))
	}

	@Test
	fun `a split row keeps its source with the original`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound", "cat")
		// the editor split "pes" into two names and renamed "hound" — "cat" was
		// removed, so the edit forks — the split source stays with the original
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "hound").id, "hound2", "")),
			listOf(trl(dog.id, "cat").id),
			additions = listOf<Pair<String, String?>>("a" to null, "b" to null)))

		assertEquals(listOf("hound2", "a", "b"), names(fork.id))
		assertEquals(listOf("pes", "cat"), names(dog.id))
	}

	@Test
	fun `a fork joins a word of the edited name`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		val cat = word(c2.id, "cat", "micka")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "cat",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		// the normal name-merge — the subject's "cat" word serves the edited chapter too
		assertEquals(cat.id, fork.id)
		assertEquals(setOf("micka", "pes"), names(cat.id).toSet())
		assertEquals(listOf("hound"), names(dog.id))
		assertEquals(cat.id, ItemRepository.children(c1.id).single().id)
	}

	@Test
	fun `a word in three chapters forks only the edited one`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = sharedIn(listOf(c1.id, c2.id, c3.id), "dog", "pes", "hound")
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		assertEquals(fork.id, ItemRepository.children(c1.id).single().id)
		assertEquals(dog.id, ItemRepository.children(c2.id).single().id)
		assertEquals(dog.id, ItemRepository.children(c3.id).single().id)
	}

	@Test
	fun `a reference occurrence does not fork the edit`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		assertTrue(ItemRepository.reference(dog.id, c3.id))
		val edited = ItemRepository.editWord(edit(dog.id, c3.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		// the shown occurrence is a reference, not a home — the edit stays in
		// place, the row itself changes and every home sees it
		assertEquals(dog.id, edited.id)
		assertEquals("doggo", ItemRepository.get(dog.id)!!.name)
		assertEquals(listOf("pes"), names(dog.id))
		assertTrue(ItemRepository.linkIsReference(c3.id, dog.id))
	}

	// -------------------------------------------------------------- references

	@Test
	fun `a fork re-points the chosen references and leaves the others`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val c4 = ItemRepository.createChapter(subject.id, "c4", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		assertTrue(ItemRepository.reference(dog.id, c3.id))
		assertTrue(ItemRepository.reference(dog.id, c4.id))
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id), referencesToFork = listOf(c3.id)))

		// the chosen one follows the new word, the other keeps showing the original
		assertTrue(ItemRepository.linkIsReference(c3.id, fork.id))
		assertFalse(ItemRepository.linkIsReference(c3.id, dog.id))
		assertTrue(ItemRepository.linkIsReference(c4.id, dog.id))
	}

	@Test
	fun `a fork recomputes the following parent's counters`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		assertTrue(ItemRepository.reference(dog.id, c3.id))
		// the answers bump the word and every holder chapter
		repeat(2) { ItemRepository.bumpCounters(listOf(dog.id, c1.id, c2.id, c3.id), false) }
		assertEquals(2, ItemRepository.get(c3.id)!!.failedTests)

		// the fork starts at zero counters — the following parent's
		// derivation must follow it down
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id), referencesToFork = listOf(c3.id)))

		assertTrue(ItemRepository.linkIsReference(c3.id, fork.id))
		// the original keeps its plain holders' counters, the follower does not
		assertEquals(2, ItemRepository.get(dog.id)!!.failedTests)
		assertEquals(2, ItemRepository.get(c2.id)!!.failedTests)
		assertEquals(0, ItemRepository.get(c3.id)!!.failedTests)
	}

	@Test
	fun `a fork without chosen references leaves them all on the original`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		assertTrue(ItemRepository.reference(dog.id, c3.id))
		val fork = ItemRepository.editWord(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(trl(dog.id, "pes").id, "pes", "")),
			listOf(trl(dog.id, "hound").id)))

		assertTrue(fork.id != dog.id)
		assertTrue(ItemRepository.linkIsReference(c3.id, dog.id))
	}

	@Test
	fun `referencesOf lists the items that reference the word`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = sharedWord(c1.id, c2.id, "dog", "pes")
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		assertTrue(ItemRepository.reference(dog.id, c3.id))

		assertEquals(listOf(c3.id), ItemRepository.referencesOf(dog.id).map { it.id })
	}

	@Test
	fun `editForks needs a shared word, a removal and a rename`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val free = word(subject.id, "free", "x")
		val dog = sharedWord(c1.id, c2.id, "dog", "pes", "hound")
		val hound = trl(dog.id, "hound").id
		val pes = trl(dog.id, "pes").id

		// a removal on a word of one chapter is an ordinary edit
		assertFalse(ItemRepository.editForks(edit(free.id, subject.id, "free", keptOf(free.id),
			listOf(trl(free.id, "x").id))))
		// the word keeps its name — a fork would only duplicate it
		assertFalse(ItemRepository.editForks(edit(dog.id, c1.id, "dog",
			listOf(KeptTranslation(pes, "pes", "")), listOf(hound))))
		// a rename that keeps every translation is an ordinary update
		assertFalse(ItemRepository.editForks(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(pes, "pes", "")), emptyList())))
		assertFalse(ItemRepository.editForks(edit(dog.id, c1.id, "doggo", keptOf(dog.id),
			emptyList(), listOf<Pair<String, String?>>("cat" to null))))
		// the shared word, renamed, with a removal — it forks
		assertTrue(ItemRepository.editForks(edit(dog.id, c1.id, "doggo",
			listOf(KeptTranslation(pes, "pes", "")), listOf(hound))))
	}
}
