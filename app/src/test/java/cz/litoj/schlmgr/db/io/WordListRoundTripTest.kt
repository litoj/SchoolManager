package cz.litoj.schlmgr.db.io

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for the word-list format: what [WordListWriter] renders
 * must parse back into the same tree ([WordListReader]). The writer walks
 * the tree through injectable lookups, so both codecs run here without a
 * database. Every word carries a translation — the format has no line for
 * a translation-less word (the legacy parser rejects it).
 */
class WordListRoundTripTest {

	private val children = HashMap<Int, List<DbItem>>()
	private val translations = HashMap<Int, List<DbItem>>()

	@After
	fun resetSplitter() {
		WordListWriter.setWordSplitter(";")
	}

	private fun item(id: Int, name: String, description: String = "", type: ItemKind = ItemKind.WORD) =
		DbItem(id = id, name = name, description = description, type = type, passedTests = 0, failedTests = 0)

	private fun link(parent: DbItem, vararg items: DbItem) {
		children[parent.id] = items.toList()
	}

	/** Renders [roots] and parses the text back; returns the re-parsed chapters. */
	private fun roundTrip(vararg roots: DbItem): List<WordListChapter> =
		WordListReader.parse(
			WordListWriter.write(roots.toList(), { children[it] ?: emptyList() }, { translations[it] ?: emptyList() }),
			"root",
		).first.children

	@Test
	fun specialCharactersInNamesRoundTrip() {
		val subject = item(1, "subject", type = ItemKind.CHAPTER)
		val words = listOf(
			item(10, "semi;colon"),
			item(11, "eq=uals"),
			item(12, "arr→ow"),
			item(13, "br[ck]et"),
		)
		link(subject, *words.toTypedArray())
		words.forEachIndexed { idx, w -> translations[w.id] = listOf(item(900 + idx, "t$idx")) }
		val root = roundTrip(subject).single()
		assertEquals(words.map { it.name }, root.words.map { it.name })
	}

	@Test
	fun variantSyntaxNamesRoundTrip() {
		val subject = item(1, "subject", type = ItemKind.CHAPTER)
		// a `\` in a name is variant syntax, not a literal — it stays raw
		val w = item(10, "word\\/syn\\(onym)")
		link(subject, w)
		translations[w.id] = listOf(item(90, "trl"))
		val root = roundTrip(subject).single()
		assertEquals("word\\/syn\\(onym)", root.words[0].name)
	}

	@Test
	fun descriptionsRoundTrip() {
		val subject = item(1, "subject", type = ItemKind.CHAPTER)
		val w = item(10, "word", "ends with ] and [ inside")
		val t = item(90, "trl", "back\\slash and \\] escape")
		link(subject, w)
		translations[w.id] = listOf(t)
		val root = roundTrip(subject).single()
		assertEquals("ends with ] and [ inside", root.words[0].description)
		assertEquals("back\\slash and \\] escape", root.words[0].translations[0].description)
	}

	@Test
	fun multilineDescriptionRoundTrip() {
		val subject = item(1, "subject", type = ItemKind.CHAPTER)
		val w = item(10, "word", "first line\nsecond line")
		link(subject, w)
		translations[w.id] = listOf(item(90, "trl"))
		val root = roundTrip(subject).single()
		assertEquals("first line\nsecond line", root.words[0].description)
	}

	@Test
	fun nestedChaptersRoundTrip() {
		val subject = item(1, "subject", "subject description", ItemKind.CHAPTER)
		val chapter = item(2, "chapter", "chapter description", ItemKind.CHAPTER)
		val w = item(10, "word", "word description")
		val first = item(90, "first", "first description")
		val second = item(91, "second")
		link(subject, chapter)
		link(chapter, w)
		translations[w.id] = listOf(first, second)
		val root = roundTrip(subject).single()
		assertEquals("subject", root.name)
		assertEquals("subject description", root.description)
		val ch = root.children.single()
		assertEquals("chapter", ch.name)
		assertEquals("chapter description", ch.description)
		val word = ch.words.single()
		assertEquals("word", word.name)
		assertEquals("word description", word.description)
		assertEquals(listOf("first", "second"), word.translations.map { it.name })
		assertEquals("first description", word.translations[0].description)
	}

	@Test
	fun multipleContainersRoundTrip() {
		val one = item(1, "one", type = ItemKind.CHAPTER)
		val two = item(2, "two", type = ItemKind.CHAPTER)
		val w = item(10, "word")
		link(one, w)
		link(two, w)
		translations[w.id] = listOf(item(90, "trl"))
		val root = roundTrip(one, two)
		assertEquals(listOf("one", "two"), root.map { it.name })
		assertEquals("word", root[0].words.single().name)
	}

	@Test
	fun customSplitterRoundTrip() {
		WordListWriter.setWordSplitter("=")
		val subject = item(1, "subject", type = ItemKind.CHAPTER)
		val w = item(10, "eq=uals")
		link(subject, w)
		translations[w.id] = listOf(item(90, "trl"))
		val root = roundTrip(subject).single()
		assertEquals("eq=uals", root.words[0].name)
		assertEquals("trl", root.words[0].translations[0].name)
	}
}
