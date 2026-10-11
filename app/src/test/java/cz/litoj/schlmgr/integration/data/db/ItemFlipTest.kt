package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
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
 * The flip's subject-wide (name, kind) uniqueness on a real in-memory
 * database — both role changes of [ItemRepository.flip] merge into the
 * subject's row of the pair — the same-name merge an import performs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemFlipTest {

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

	@Test
	fun `a flip refreshes the covered containers' stored counters`() {
		val subject = ItemRepository.createSubject("s", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "t1", "t2")
		ItemRepository.bumpCounters(listOf(dog.id, c1.id), true)
		assertEquals(1, ItemRepository.get(c1.id)!!.passedTests)

		ItemRepository.flip(listOf(dog.id))

		// the word became a translation, so the chapter's word sum changed —
		// the stored counters follow without a reopen
		val stored = ItemRepository.get(c1.id)!!
		val derived = ItemRepository.recomputeCounters(c1.id)
		assertEquals(derived.passed, stored.passedTests)
		assertEquals(derived.failed, stored.failedTests)
	}

	private fun word(name: String): DbItem = ItemRepository.getByName(name, ItemKind.WORD).single()

	/** Fails when the subject holds two word or translation rows of one name. */
	private fun assertUnique(subject: DbItem) {
		val seen = HashSet<String>()
		for (item in ItemRepository.subgraph(subject.id))
			if (item.type == ItemKind.WORD || item.type == ItemKind.TRANSLATION)
				assertTrue("duplicate ${item.type} '${item.name}' in the subject",
					seen.add("${item.type}:${item.name}"))
	}

	@Test
	fun `a plain flip swaps the roles`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes", "hound")
		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// both new words share the flipped word row as their one translation
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("hound").id).map { it.name })
	}

	@Test
	fun `a word selected beside its chapter flips once`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null, 0)
		// the selection holds the word and the chapter that contains it
		val dog = word(c1.id, "dog", "pes")
		ItemRepository.flip(listOf(dog.id, c1.id))

		// one flip: "pes" is a word whose translation is "dog" — the
		// chapter pass did not flip it right back
		assertUnique(subject)
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a merge's counter adoption refreshes the merged word's chapters`() {
		val subject = ItemRepository.createSubject("english", null)
		val chA = ItemRepository.createChapter(subject.id, "chA", null, 0)
		val chB = ItemRepository.createChapter(subject.id, "chB", null, 1)
		// chA's translation "beta" carries more tests than chB's word "beta"
		val alpha = word(chA.id, "alpha", "beta")
		val betaWord = word(chB.id, "beta", "x")
		val trlBeta = ItemRepository.translations(alpha.id).single()
		repeat(3) { ItemRepository.bumpCounters(listOf(trlBeta.id), true) }
		ItemRepository.bumpCounters(listOf(betaWord.id, chB.id), false)

		ItemRepository.flip(listOf(alpha.id))

		// the flipped translation merged into chB's word, adopting its
		// counters — chB's stored derivation follows
		assertEquals(3, ItemRepository.get(chB.id)!!.passedTests)
		assertEquals(0, ItemRepository.get(chB.id)!!.failedTests)
	}

	@Test
	fun `a referencing sharer keeps its reference through the flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null, 0)
		val other = ItemRepository.createChapter(subject.id, "other", null, 1)
		val dog = word(c1.id, "dog", "pes")
		// a chapter references the translation row itself
		val pesTrl = ItemRepository.translations(dog.id).single()
		assertTrue(ItemRepository.reference(pesTrl.id, other.id))

		ItemRepository.flip(listOf(dog.id))

		// the sharer loop replaced the row the chapter referenced — the
		// link stays a reference, not plain ownership
		val replacement = ItemRepository.getByName("pes", ItemKind.TRANSLATION).single()
		assertTrue(ItemRepository.linkIsReference(other.id, replacement.id))
	}

	@Test
	fun `a flipped word merges into the subject's same-named translation`() {
		val subject = ItemRepository.createSubject("english", null)
		word(subject.id, "cat", "dog")
		val dog = word(subject.id, "dog", "pes")
		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// the subject's one "dog" translation row serves both words — the
		// flipped row did not become a second one
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.TRANSLATION).size)
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a shared translation leaves the sharers one row of the name`() {
		val subject = ItemRepository.createSubject("english", null)
		word(subject.id, "cat", "pes")
		val dog = word(subject.id, "dog", "pes")
		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// "cat" keeps a "pes" translation — the flipped row became the word "pes"
		assertEquals(listOf("pes"), ItemRepository.translations(word("cat").id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a translation named like a word of the subject merges into that word`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		word(subject.id, "pes", "cat")
		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// one word "pes" carries the translations of both merged rows
		assertEquals(setOf("cat", "dog"), ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `three words sharing one translation flip into one word with three translations`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val hound = word(subject.id, "hound", "pes")
		val cur = word(subject.id, "cur", "pes")
		ItemRepository.flip(listOf(dog.id, hound.id, cur.id))

		assertUnique(subject)
		// one word row "pes" carries all three merged words as its translations
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `three same-named translation rows flip into one word`() {
		val subject = ItemRepository.createSubject("english", null)
		// three separate "pes" rows as the corrupted starting state
		val dog = word(subject.id, "dog", "x")
		val hound = word(subject.id, "hound", "y")
		val cur = word(subject.id, "cur", "z")
		for ((parent, name) in listOf(dog to "pes", hound to "pes", cur to "pes")) {
			val trl = ItemRepository.legacyInsert(name, null, ItemKind.TRANSLATION, 0, 0)
			ItemRepository.legacyLinkAtEnd(parent.id, trl)
		}
		ItemRepository.flip(listOf(dog.id, hound.id, cur.id))

		assertUnique(subject)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `three separate flips of words sharing one translation make one word`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val hound = word(subject.id, "hound", "pes")
		val cur = word(subject.id, "cur", "pes")
		ItemRepository.flip(listOf(dog.id))
		ItemRepository.flip(listOf(hound.id))
		ItemRepository.flip(listOf(cur.id))

		assertUnique(subject)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `a chapter flip merges its words sharing one translation`() {
		val subject = ItemRepository.createSubject("english", null)
		val ch = ItemRepository.createChapter(subject.id, "ch", null)
		word(ch.id, "dog", "pes")
		word(ch.id, "hound", "pes")
		word(ch.id, "cur", "pes")
		ItemRepository.flip(listOf(ch.id))

		assertUnique(subject)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `words sharing a translation across several chapters flip into one word`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val c3 = ItemRepository.createChapter(subject.id, "c3", null)
		val dog = word(c1.id, "dog", "pes")
		val hound = word(c2.id, "hound", "pes")
		val cur = word(c3.id, "cur", "pes")
		ItemRepository.flip(listOf(dog.id, hound.id, cur.id))

		assertUnique(subject)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `flipping duplicate word rows of one name returns the shared translation`() {
		val subject = ItemRepository.createSubject("english", null)
		// three word rows "pes" in one chapter as the corrupted starting state
		val rows = listOf("dog", "hound", "cur").map { trl ->
			val w = ItemRepository.legacyInsert("pes", null, ItemKind.WORD, 0, 0)
			ItemRepository.legacyLinkAtEnd(subject.id, w)
			ItemRepository.legacyLinkAtEnd(w, ItemRepository.legacyInsert(trl, null, ItemKind.TRANSLATION, 0, 0))
			w
		}
		ItemRepository.flip(rows)

		assertUnique(subject)
		// the inverse of the corrupted state — three words sharing one translation
		assertEquals(listOf("cur", "dog", "hound"),
			ItemRepository.children(subject.id).map { it.name }.sorted())
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.TRANSLATION).size)
	}

	@Test
	fun `two words of each other's names flip into their inverse`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		word(subject.id, "pes", "dog")
		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// both merges fired at once: "pes" is the only word and "dog" its translation
		assertEquals(1, ItemRepository.subgraph(subject.id).count { it.type == ItemKind.WORD })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a referenced word flips its references along`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val chapter = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val word = word(chapter.id, "das Haus", "house")
		val referencing = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		ItemRepository.reference(word.id, referencing.id)

		ItemRepository.flip(listOf(word.id))

		// the reference follows the flip — the new word shows in the
		// referencing chapter, still as a reference
		val flipped = word("house")
		assertTrue(ItemRepository.linkIsReference(referencing.id, flipped.id) == true)
		assertEquals(listOf("house"), ItemRepository.children(referencing.id).map { it.name })
		// the flipped word is gone from there — it is a translation now (the
		// facade's false covers 'no such link')
		assertFalse(ItemRepository.linkIsReference(referencing.id, word.id))
	}

	@Test
	fun `a reference survives the flip's merge into an existing translation`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val chapter = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		word(chapter.id, "das Tier", "die Katze")
		val cat = word(chapter.id, "die Katze", "animal")
		val referencing = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		ItemRepository.reference(cat.id, referencing.id)

		ItemRepository.flip(listOf(cat.id))

		// cat itself dies in the merge — the reference still follows the flip
		assertTrue(ItemRepository.linkIsReference(referencing.id, word("animal").id) == true)
	}

	@Test
	fun `the flip analysis names the words linked outside the selection`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val c1 = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val c2 = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		val haus = word(c1.id, "das Haus", "house")
		val schule = word(c1.id, "die Schule", "school")
		ItemRepository.legacyLinkAtEnd(c2.id, schule.id)

		// schule also lives in c2 — haus stays inside the selection
		val conflicts = ItemRepository.flipConflicts(listOf(c1.id), listOf(haus.id, schule.id))
		assertEquals(listOf("die Schule"), conflicts.map { it.word.name })
		assertEquals(listOf("Lektion 2"), conflicts.single().parents.map { it.name })

		// a link outside through a reference does not conflict — it follows
		ItemRepository.reference(haus.id, c2.id)
		assertEquals(1, ItemRepository.flipConflicts(listOf(c1.id), listOf(haus.id, schule.id)).size)
	}

	@Test
	fun `the flip analysis covers a selected chapter's subtree`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val c1 = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val c2 = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		val inner = ItemRepository.createChapter(c2.id, "Innen", null)
		val haus = word(inner.id, "das Haus", "house")
		ItemRepository.legacyLinkAtEnd(c1.id, haus.id)

		// the word lives under the selected chapter's sub-chapter and in c1:
		// c1 is outside the selection
		val conflicts = ItemRepository.flipConflicts(listOf(subject.id), listOf(c2.id))
		assertEquals(listOf("das Haus"), conflicts.map { it.word.name })

		// selecting c1 too covers the word's every chapter
		assertTrue(ItemRepository.flipConflicts(listOf(subject.id), listOf(c1.id, c2.id)).isEmpty())
	}

	@Test
	fun `the flip analysis covers several acting parents`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val c1 = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val c2 = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		val haus = word(c1.id, "das Haus", "house")
		val schule = word(c2.id, "die Schule", "school")

		// a search selection — each word picked from its own chapter
		assertTrue(ItemRepository.flipConflicts(listOf(c1.id, c2.id), listOf(haus.id, schule.id)).isEmpty())

		// schule picked from c2 only, but it also lives in c1 — outside
		ItemRepository.legacyLinkAtEnd(c1.id, schule.id)
		val conflicts = ItemRepository.flipConflicts(listOf(c2.id), listOf(schule.id))
		assertEquals(listOf("die Schule"), conflicts.map { it.word.name })
		assertEquals(listOf("Lektion 1"), conflicts.single().parents.map { it.name })
	}
}
