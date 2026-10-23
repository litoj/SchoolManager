package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipResolution
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
 * - the subject-wide name and kind uniqueness holds after every flip.
 * - each role change merges into the subject row, so the pair stays unique.
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

		// the word became a translation, so the word sum of the chapter changed,
		// and the stored counters follow without a reopen
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
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("hound").id).map { it.name })
	}

	@Test
	fun `a word selected beside its chapter flips once`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null, 0)
		val dog = word(c1.id, "dog", "pes")
		ItemRepository.flip(listOf(dog.id, c1.id))

		// one flip: "pes" is a word whose translation is "dog", so the chapter
		// pass did not flip it right back
		assertUnique(subject)
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a merge's counter adoption refreshes the merged word's chapters`() {
		val subject = ItemRepository.createSubject("english", null)
		val chA = ItemRepository.createChapter(subject.id, "chA", null, 0)
		val chB = ItemRepository.createChapter(subject.id, "chB", null, 1)
		// the translation "beta" of chA carries more tests than the word "beta" of chB
		val alpha = word(chA.id, "alpha", "beta")
		val betaWord = word(chB.id, "beta", "x")
		val trlBeta = ItemRepository.translations(alpha.id).single()
		repeat(3) { ItemRepository.bumpCounters(listOf(trlBeta.id), true) }
		ItemRepository.bumpCounters(listOf(betaWord.id, chB.id), false)

		ItemRepository.flip(listOf(alpha.id))

		// the flipped translation merged into the word of chB and adopted its
		// counters, so the stored derivation of chB follows
		assertEquals(3, ItemRepository.get(chB.id)!!.passedTests)
		assertEquals(0, ItemRepository.get(chB.id)!!.failedTests)
	}

	@Test
	fun `a referencing sharer keeps its reference through the flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null, 0)
		val other = ItemRepository.createChapter(subject.id, "other", null, 1)
		val dog = word(c1.id, "dog", "pes")
		val pesTrl = ItemRepository.translations(dog.id).single()
		assertTrue(ItemRepository.reference(pesTrl.id, other.id))

		ItemRepository.flip(listOf(dog.id))

		// the sharer loop replaced the row the chapter referenced, so the link
		// stays a reference, not plain ownership
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
		// the one "dog" translation row of the subject serves both words, so the
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
		// "cat" keeps a "pes" translation, because the flipped row became the word "pes"
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
		// the inverse of the corrupted state, three words sharing one translation
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
		assertEquals(1, ItemRepository.subgraph(subject.id).count { it.type == ItemKind.WORD })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a mutual pair flips by trading places`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = word(c1.id, "dog", "pes")
		val pes = word(c2.id, "pes", "dog")
		ItemRepository.setDescription(dog.id, "dd")
		ItemRepository.setDescription(pes.id, "pp")
		ItemRepository.bumpCounters(listOf(dog.id), true)
		ItemRepository.bumpCounters(listOf(pes.id), false)

		ItemRepository.flip(listOf(dog.id, pes.id))

		assertUnique(subject)
		// the trade keeps both rows, so no merge drops a pair
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.WORD).size)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(1, ItemRepository.getByName("dog", ItemKind.TRANSLATION).size)
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.TRANSLATION).size)
		// the trade swaps roles in place, so each chapter keeps its pair
		assertEquals(listOf("pes"), ItemRepository.children(c1.id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.children(c2.id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(word("dog").id).map { it.name })
		// descriptions and counters follow the traded places, so each chapter keeps its data
		assertEquals("dd", ItemRepository.get(word("pes").id)!!.description)
		assertEquals("pp", ItemRepository.get(word("dog").id)!!.description)
		assertEquals(1, ItemRepository.get(word("pes").id)!!.passedTests)
		assertEquals(1, ItemRepository.get(word("dog").id)!!.failedTests)
	}

	@Test
	fun `a partial cycle with an extra translation flips through the machinery`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes", "mutt")
		val pes = word(subject.id, "pes", "dog")
		ItemRepository.flip(listOf(dog.id, pes.id))

		assertUnique(subject)
		// the extra translation blocks the trade, so the merge path must resolve it
		assertEquals(listOf("dog"), ItemRepository.translations(word("mutt").id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(word("dog").id).map { it.name })
	}

	@Test
	fun `a mutual pair across subjects flips through the machinery, not the trade`() {
		val subjectA = ItemRepository.createSubject("english", null)
		val subjectB = ItemRepository.createSubject("deutsch", null)
		val dog = word(subjectA.id, "dog", "pes")
		val pes = word(subjectB.id, "pes", "dog")

		ItemRepository.flip(listOf(dog.id, pes.id))

		// the trade stays inside one subject, so each side resolves its own pair
		assertUnique(subjectA)
		assertUnique(subjectB)
		assertEquals(listOf("pes"), ItemRepository.children(subjectA.id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.children(subjectB.id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a word named like another's translation flips first in one run`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val dog = word(c1.id, "dog", "pes")
		val pes = word(c1.id, "pes", "mutt")
		ItemRepository.flip(listOf(dog.id, pes.id))

		assertUnique(subject)
		// one run must order the flips, or the second undoes the first
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(word("mutt").id).map { it.name })
	}

	@Test
	fun `an outside sharer keeps the original row through the other word's flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes")
		val shared = ItemRepository.translations(dog.id).single()
		ItemRepository.setDescription(shared.id, "shared")
		ItemRepository.bumpCounters(listOf(shared.id), true)

		ItemRepository.flip(listOf(dog.id))

		assertUnique(subject)
		// the outside word keeps the row, so the contents and counters stay intact
		val kept = ItemRepository.translations(cat.id).single()
		assertEquals(shared.id, kept.id)
		assertEquals("shared", kept.description)
		assertEquals(1, kept.passedTests)
		// the flip runs on a copy, so the kept row stays untouched
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

		// the reference follows the flip, so the new word shows in the referencing
		// chapter, still as a reference
		val flipped = word("house")
		assertTrue(ItemRepository.linkIsReference(referencing.id, flipped.id) == true)
		assertEquals(listOf("house"), ItemRepository.children(referencing.id).map { it.name })
		// the flipped word is gone from there, because it is a translation now
		// the false from the facade covers the missing link
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

		// cat itself dies in the merge, and the reference still follows the flip
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

		// schule also lives in c2, so haus stays inside the selection
		val conflicts = ItemRepository.startFlip(listOf(c1.id), listOf(haus.id, schule.id)).conflicts
		assertEquals(listOf("die Schule"), conflicts.map { it.row.name })
		assertEquals(listOf("Lektion 2"), conflicts.single().outside.map { it.name })
		assertEquals(listOf("die Schule"), conflicts.single().scheduled.map { it.name })

		// a reference shows the flip too, so it conflicts like a plain link
		ItemRepository.reference(haus.id, c2.id)
		val withRef = ItemRepository.startFlip(listOf(c1.id), listOf(haus.id, schule.id)).conflicts
		assertEquals(setOf("das Haus", "die Schule"), withRef.map { it.row.name }.toSet())
	}

	@Test
	fun `the flip analysis covers a selected chapter's subtree`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val c1 = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val c2 = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		val inner = ItemRepository.createChapter(c2.id, "Innen", null)
		val haus = word(inner.id, "das Haus", "house")
		ItemRepository.legacyLinkAtEnd(c1.id, haus.id)

		// the word lives under the sub-chapter and in c1, which stays outside
		val conflicts = ItemRepository.startFlip(listOf(subject.id), listOf(c2.id)).conflicts
		assertEquals(listOf("das Haus"), conflicts.map { it.row.name })

		// selecting c1 too covers every chapter of the word
		assertTrue(ItemRepository.startFlip(listOf(subject.id), listOf(c1.id, c2.id)).conflicts.isEmpty())
	}

	@Test
	fun `the flip analysis covers several acting parents`() {
		val subject = ItemRepository.createSubject("Deutsch", null)
		val c1 = ItemRepository.createChapter(subject.id, "Lektion 1", null)
		val c2 = ItemRepository.createChapter(subject.id, "Lektion 2", null)
		val haus = word(c1.id, "das Haus", "house")
		val schule = word(c2.id, "die Schule", "school")

		// a search selection picks each word from its own chapter
		assertTrue(ItemRepository.startFlip(listOf(c1.id, c2.id), listOf(haus.id, schule.id)).conflicts.isEmpty())

		// schule comes from c2 only, but it also lives in c1, which stays outside
		ItemRepository.legacyLinkAtEnd(c1.id, schule.id)
		val conflicts = ItemRepository.startFlip(listOf(c2.id), listOf(schule.id)).conflicts
		assertEquals(listOf("die Schule"), conflicts.map { it.row.name })
		assertEquals(listOf("Lektion 1"), conflicts.single().outside.map { it.name })
	}

	@Test
	fun `the flip analysis names a translation held outside the selection`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes")

		val conflicts = ItemRepository.startFlip(listOf(subject.id), listOf(dog.id)).conflicts
		// the shared row spans the selection. dog holds it and cat stays outside
		assertEquals(listOf("pes"), conflicts.map { it.row.name })
		assertEquals(listOf("cat"), conflicts.single().outside.map { it.name })
		assertEquals(listOf("dog"), conflicts.single().scheduled.map { it.name })
	}

	@Test
	fun `a kept shared translation joins the outside word to the flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes")
		val pes = ItemRepository.translations(dog.id).single()

		val analysis = ItemRepository.startFlip(listOf(subject.id), listOf(dog.id))
		// a keep without change leaves nothing to ask, so the flip can run
		val next = ItemRepository.advanceFlip(analysis, listOf(
			FlipResolution(pes.id, false, pes.name, pes.description)))
		assertTrue(next.conflicts.isEmpty())

		ItemRepository.commitFlip(next)

		assertUnique(subject)
		// one word "pes" carries both, because the flip merges the kept row back
		assertEquals(setOf("cat", "dog"), ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `an edited shared translation detaches a custom copy for the flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes")
		val pes = ItemRepository.translations(dog.id).single()
		ItemRepository.setDescription(pes.id, "shared")

		val analysis = ItemRepository.startFlip(listOf(subject.id), listOf(dog.id))
		// the trim keeps the same word, so only the edit detaches a copy
		val next = ItemRepository.advanceFlip(analysis, listOf(
			FlipResolution(pes.id, false, pes.name + " ", pes.description)))
		assertTrue(next.conflicts.isEmpty())

		ItemRepository.commitFlip(next)

		assertUnique(subject)
		// the copy protects the outside word, which keeps the row and contents
		val kept = ItemRepository.translations(cat.id).single()
		assertEquals(pes.id, kept.id)
		assertEquals("shared", kept.description)
		// the flip runs on the custom copy, so the original stays shared
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a removed shared translation drops its scheduled words from the flip`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes")
		val pes = ItemRepository.translations(dog.id).single()

		val analysis = ItemRepository.startFlip(listOf(subject.id), listOf(dog.id))
		val next = ItemRepository.advanceFlip(analysis, listOf(
			FlipResolution(pes.id, true, pes.name, pes.description)))
		assertTrue(next.conflicts.isEmpty())

		ItemRepository.commitFlip(next)

		// a remove drops the scheduled words, so nothing flips
		assertEquals(listOf("pes"), ItemRepository.translations(dog.id).map { it.name })
		assertEquals(listOf("pes"), ItemRepository.translations(cat.id).map { it.name })
		assertEquals(0, ItemRepository.getByName("pes", ItemKind.WORD).size)
	}

	@Test
	fun `a kept multi-chapter word flips in every chapter`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = word(c1.id, "dog", "pes")
		ItemRepository.legacyLinkAtEnd(c2.id, dog.id)

		val analysis = ItemRepository.startFlip(listOf(c1.id), listOf(dog.id))
		// a keep adds the outside links, so the flip covers every chapter
		val next = ItemRepository.advanceFlip(analysis, listOf(
			FlipResolution(dog.id, false, dog.name, dog.description)))
		assertTrue(next.conflicts.isEmpty())

		ItemRepository.commitFlip(next)

		assertUnique(subject)
		// a keep flips both chapters, so the word cannot survive in c2
		assertEquals(setOf("pes"), ItemRepository.children(c1.id).map { it.name }.toSet())
		assertEquals(setOf("pes"), ItemRepository.children(c2.id).map { it.name }.toSet())
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `an edited multi-chapter word forks for the flip, the outside keeps it`() {
		val subject = ItemRepository.createSubject("english", null)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null)
		val dog = word(c1.id, "dog", "pes")
		ItemRepository.legacyLinkAtEnd(c2.id, dog.id)

		val analysis = ItemRepository.startFlip(listOf(c1.id), listOf(dog.id))
		// the trim keeps the same word, so the edit forks the row for the flip
		val next = ItemRepository.advanceFlip(analysis, listOf(
			FlipResolution(dog.id, false, dog.name + " ", dog.description)))
		assertTrue(next.conflicts.isEmpty())

		ItemRepository.commitFlip(next)

		assertUnique(subject)
		// the fork protects c2, which keeps the word itself
		assertEquals(listOf("dog"), ItemRepository.children(c2.id).map { it.name })
		// the flip runs on the fork, so c1 changes and c2 stays
		assertEquals(listOf("pes"), ItemRepository.children(c1.id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
	}

	@Test
	fun `a kept conflict's new words run through the check again`() {
		val subject = ItemRepository.createSubject("english", null)
		val dog = word(subject.id, "dog", "pes")
		val cat = word(subject.id, "cat", "pes", "kocka")
		val lv = word(subject.id, "lv", "kocka")

		// keeping "pes" pulls cat in. the "kocka" of cat spans outside, so ask again
		var analysis = ItemRepository.startFlip(listOf(subject.id), listOf(dog.id))
		assertEquals(listOf("pes"), analysis.conflicts.map { it.row.name })
		analysis = ItemRepository.advanceFlip(analysis, analysis.conflicts.map {
			FlipResolution(it.row.id, false, it.row.name, it.row.description) })
		assertEquals(listOf("kocka"), analysis.conflicts.map { it.row.name })
		analysis = ItemRepository.advanceFlip(analysis, analysis.conflicts.map {
			FlipResolution(it.row.id, false, it.row.name, it.row.description) })
		assertTrue(analysis.conflicts.isEmpty())

		ItemRepository.commitFlip(analysis)

		assertUnique(subject)
		assertEquals(setOf("cat", "dog"), ItemRepository.translations(word("pes").id).map { it.name }.toSet())
		assertEquals(setOf("cat", "lv"), ItemRepository.translations(word("kocka").id).map { it.name }.toSet())
	}
}
