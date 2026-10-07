package cz.litoj.schlmgr.db

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The flip's subject-wide (name, kind) uniqueness on a real in-memory
 * database: both role changes of [ItemRepository.flip] merge into the
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
		// both new words share the old word row as their one translation
		assertEquals(listOf("dog"), ItemRepository.translations(word("pes").id).map { it.name })
		assertEquals(listOf("dog"), ItemRepository.translations(word("hound").id).map { it.name })
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
		// "cat" keeps a "pes" translation; the flipped row became the word "pes"
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
		// one word "pes" carries the translations of both former rows
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
		// one word row "pes" carries all three former words as its translations
		assertEquals(1, ItemRepository.getByName("pes", ItemKind.WORD).size)
		assertEquals(setOf("dog", "hound", "cur"),
			ItemRepository.translations(word("pes").id).map { it.name }.toSet())
	}

	@Test
	fun `three same-named translation rows flip into one word`() {
		val subject = ItemRepository.createSubject("english", null)
		// the state the pre-fix flipper left behind: three separate "pes" rows
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
	fun `flipping the pre-fix word rows of one name returns the shared translation`() {
		val subject = ItemRepository.createSubject("english", null)
		// the state the pre-fix flipper left: three word rows "pes" in one chapter
		val rows = listOf("dog", "hound", "cur").map { trl ->
			val w = ItemRepository.legacyInsert("pes", null, ItemKind.WORD, 0, 0)
			ItemRepository.legacyLinkAtEnd(subject.id, w)
			ItemRepository.legacyLinkAtEnd(w, ItemRepository.legacyInsert(trl, null, ItemKind.TRANSLATION, 0, 0))
			w
		}
		ItemRepository.flip(rows)

		assertUnique(subject)
		// the inverse of the corrupted state: three words sharing one translation
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
}
