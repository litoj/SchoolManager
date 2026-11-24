package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListProblem
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * - no database is involved.
 * - every word carries a translation, because the format has no line for none.
 * - a name the format cannot carry refuses the write.
 * - CR, LF and their pair each end one line.
 * - a blank piece of a split line names no translation.
 */
class WordListFormatTest {

	/** The splitter must return to its earlier value, because later classes read the default. */
	private val splitterWas = WordListWriter.wordTranslationSplitter

	@After
	fun resetSplitter() {
		WordListWriter.setWordTranslationSplitter(splitterWas)
	}

	/** Collects the stream while the tree is built, parent first and children after. */
	private class StreamBuilder {
		val items = ArrayList<StreamItem>()

		fun add(item: StreamItem): StreamItem {
			items.add(item)
			return item
		}

		fun chapter(name: String, description: String = "", parent: StreamItem? = null): StreamItem =
			add(StreamItem(DbItem(name = name, description = description, type = ItemKind.CHAPTER), parent))

		fun word(
			name: String, parent: StreamItem?, description: String,
			vararg translations: Pair<String, String>,
		): StreamItem {
			val w = add(StreamItem(DbItem(name = name, description = description, type = ItemKind.WORD), parent))
			for ((trlName, trlDesc) in translations)
				add(StreamItem(DbItem(name = trlName, description = trlDesc,
					type = ItemKind.TRANSLATION), w))
			return w
		}

		fun note(name: String, description: String, parent: StreamItem?): StreamItem =
			add(StreamItem(DbItem(name = name, description = description, type = ItemKind.NOTE), parent))
	}

	private fun roundTrip(builder: StreamBuilder): List<StreamItem> =
		WordListImporter("root", WordListWriter.write(builder.items)).items

	@Test
	fun `special characters in names round trip`() {
		val b = StreamBuilder()
		val subject = b.chapter("subject")
		b.word("semi;colon", subject, "", "t" to "")
		b.word("eq=uals", subject, "", "t" to "")
		b.word("arr→ow", subject, "", "t" to "")
		b.word("br[ck]et", subject, "", "t" to "")
		val back = roundTrip(b)
		assertEquals(listOf("semi;colon", "eq=uals", "arr→ow", "br[ck]et"),
			back.filter { it.item.type == ItemKind.WORD }.map { it.item.name })
	}

	@Test
	fun `variant-syntax names round trip`() {
		val b = StreamBuilder()
		// a backslash in a name is variant syntax, not a literal, so it stays raw
		b.word("word\\/syn\\(onym)", b.chapter("subject"), "", "trl" to "")
		assertEquals("word\\/syn\\(onym)",
			roundTrip(b).first { it.item.type == ItemKind.WORD }.item.name)
	}

	@Test
	fun `descriptions round trip`() {
		val b = StreamBuilder()
		val subject = b.chapter("subject")
		val w = b.word("word", subject, "ends with ] and [ inside")
		b.add(StreamItem(DbItem(name = "trl", description = "back\\slash and \\] escape",
			type = ItemKind.TRANSLATION), w))
		b.word("two lines", subject, "first line\nsecond line", "trl" to "")
		val back = roundTrip(b)
		assertEquals("ends with ] and [ inside",
			back.first { it.item.type == ItemKind.WORD }.item.description)
		assertEquals("back\\slash and \\] escape",
			back.first { it.item.type == ItemKind.TRANSLATION }.item.description)
		// the writer wraps a description over its own lines, the reader trims the wrapper
		assertEquals("first line\nsecond line",
			back.last { it.item.type == ItemKind.WORD }.item.description)
	}

	@Test
	fun `nested chapters round trip`() {
		val b = StreamBuilder()
		val subject = b.chapter("subject", "subject description")
		val ch = b.chapter("chapter", "chapter description", subject)
		b.word("word", ch, "word description", "first" to "first description", "second" to "")
		val back = roundTrip(b)
		val root = back.first()
		assertEquals("subject", root.item.name)
		assertEquals("subject description", root.item.description)
		val backChapter = back.first { it.item.type == ItemKind.CHAPTER && it.item.name == "chapter" }
		assertEquals("chapter description", backChapter.item.description)
		assertEquals("word", back.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals("word description", back.first { it.item.type == ItemKind.WORD }.item.description)
		assertEquals(listOf("first", "second"), back.filter { it.item.type == ItemKind.TRANSLATION }
			.map { it.item.name })
	}

	@Test
	fun `multiple containers round trip`() {
		val b = StreamBuilder()
		val one = b.chapter("one")
		val two = b.chapter("two")
		b.word("word", one, "", "trl" to "")
		b.word("word", two, "", "trl" to "")
		val back = roundTrip(b)
		assertEquals(listOf("one", "two"),
			back.filter { it.item.type == ItemKind.CHAPTER && it.parent == null }.map { it.item.name })
	}

	@Test
	fun `word and note lines round trip at the root and inside a chapter`() {
		val b = StreamBuilder()
		val subject = b.chapter("subject")
		b.note("note", "note description", subject)
		b.note("plain note", "plain body", subject)
		b.note("back\\slash note", "escaped body", subject)
		b.word("bare word", null, "word description", "trl" to "trl description")
		b.note("bare note", "note description", null)
		val back = roundTrip(b)

		val notes = back.filter { it.item.type == ItemKind.NOTE }
		assertEquals(listOf("note", "plain note", "back\\slash note", "bare note"),
			notes.map { it.item.name })
		assertEquals("note description", notes[0].item.description)
		assertEquals("subject", notes[0].parent?.item?.name)
		// a root line keeps its kind, with no empty chapter wrapper around it
		val word = back.first { it.item.type == ItemKind.WORD }
		assertEquals(null, word.parent)
		assertEquals("bare word", word.item.name)
		assertEquals("word description", word.item.description)
		assertEquals("trl", back.first { it.item.type == ItemKind.TRANSLATION }.item.name)
		assertEquals(null, notes.last().parent)
		assertEquals("note description", notes.last().item.description)

		// one bare root writes one line, its translations joined by a backslash
		val lone = StreamItem(DbItem(name = "w1", description = "", type = ItemKind.WORD), null)
		val lines = listOf(lone,
			StreamItem(DbItem(name = "t1", description = "", type = ItemKind.TRANSLATION), lone),
			StreamItem(DbItem(name = "t2", description = "", type = ItemKind.TRANSLATION), lone))
		assertEquals("w1=t1\\t2\n", WordListWriter.write(lines))
	}

	@Test
	fun `the file keeps the order of its mixed lines`() {
		// words, notes and chapters interleave in the file, so the stream keeps
		// that order and the link positions of the import match the file
		val b = StreamBuilder()
		val ch = b.chapter("ch")
		b.word("w1", ch, "", "t1" to "")
		b.note("n1", "the body", ch)
		b.word("w2", ch, "", "t2" to "")

		assertEquals(listOf("c:ch", "w:w1", "t:t1", "n:n1", "w:w2", "t:t2"),
			roundTrip(b).map { it.item.type.kindPrefix() + it.item.name })
	}

	@Test
	fun `a custom splitter round trips`() {
		WordListWriter.setWordTranslationSplitter(";")
		val b = StreamBuilder()
		b.word("semi;colon", b.chapter("subject"), "", "trl" to "")
		val back = roundTrip(b)
		assertEquals("semi;colon", back.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals("trl", back.first { it.item.type == ItemKind.TRANSLATION }.item.name)
	}

	@Test
	fun `a splitter the reader cannot parse is refused`() {
		WordListWriter.setWordTranslationSplitter("=")
		WordListWriter.setWordTranslationSplitter(":")
		assertEquals("=", WordListWriter.wordTranslationSplitter)
		WordListWriter.setWordTranslationSplitter("a b")
		assertEquals("=", WordListWriter.wordTranslationSplitter)
		WordListWriter.setWordTranslationSplitter("→")
		assertEquals("=", WordListWriter.wordTranslationSplitter)
	}

	@Test
	fun `a backslash at a name's end round trips`() {
		val b = StreamBuilder()
		b.word("ends\\", b.chapter("subject"), "", "a\\" to "", "b" to "")
		val back = roundTrip(b)
		// the trailing run doubles on the way out and halves on the way back, so
		// the exact names survive
		assertEquals("ends\\", back.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals(listOf("a\\", "b"),
			back.filter { it.item.type == ItemKind.TRANSLATION }.map { it.item.name })

		// the second cycle halves what the first doubled, so nothing drifts
		val back2 = WordListImporter("subject", WordListWriter.write(back)).items
		assertEquals("ends\\", back2.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals(listOf("a\\", "b"),
			back2.filter { it.item.type == ItemKind.TRANSLATION }.map { it.item.name })
	}

	@Test
	fun `a backslash run before a separator stays stable`() {
		// a stored name with one backslash before an escaped separator has no exact
		// encoding: the write evens the run and the next cycle writes the same text
		fun stream(name: String): List<StreamItem> {
			val word = StreamItem(DbItem(name = name, description = "", type = ItemKind.WORD), null)
			return listOf(word,
				StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), word))
		}

		val odd = WordListWriter.write(stream("a\\;"))
		// the run became even, so the name carries two backslashes now
		assertEquals("a\\\\;", WordListReader.parse(odd, "c").words.single().name)
		assertEquals(odd, WordListWriter.write(stream("a\\\\;")))

		val even = WordListWriter.write(stream("a\\\\="))
		assertEquals("a\\\\=", WordListReader.parse(even, "c").words.single().name)
	}

	@Test
	fun `a blank name refuses the write`() {
		val refusals = mapOf(
			"an empty word name" to DbItem(name = "", description = "", type = ItemKind.WORD),
			"an empty note name" to DbItem(name = "", description = "", type = ItemKind.NOTE),
			"a blank word name" to DbItem(name = " ", description = "", type = ItemKind.WORD),
		)

		for ((name, item) in refusals) {
			// the reader trims the line, so a blank name would read back as a note
			val root = StreamItem(item, null)
			val items = ArrayList<StreamItem>()
			items.add(root)
			if (item.type == ItemKind.WORD)
				items.add(StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), root))
			try {
				WordListWriter.write(items)
				fail("$name must refuse the write")
			} catch (e: IllegalArgumentException) {
				assertTrue(e.message!!.contains("word-list line"))
			}
		}
	}

	@Test
	fun `CR LF and their pair each end one line`() {
		val endings = mapOf("LF" to "\n", "CRLF" to "\r\n", "a lone CR" to "\r")

		for ((name, ending) in endings) {
			val root = WordListReader.parse("ch {${ending}word;t${ending}}${ending}", "c")
			assertEquals("$name keeps its chapter", "ch", root.children.single().name)
			assertEquals("$name keeps its word", "word", root.children.single().words.single().name)

			// a blank line ends too, so the failure still names its own line
			val error = assertThrows(WordListParseException::class.java) {
				WordListReader.parse("ch {${ending}${ending}word without separator${ending}}${ending}", "c")
			}
			assertEquals("$name counts the blank line", 3, error.lineNumber)
			assertEquals("$name keeps the failing text", "word without separator", error.sourceLine)
			assertEquals("$name points behind its text", error.sourceLine.length, error.problemIndex)
		}
	}

	@Test
	fun `a blank piece adds nothing at the line end`() {
		// a trailing backslash splits the translations and the blank piece trims to
		// no name, so the word keeps its real translation alone
		val root = WordListReader.parse("w;t\\ \n", "c")
		assertEquals(listOf("t"), root.words.single().translations.map { it.name })
	}

	@Test
	fun `a chapter closer admits only trailing whitespace`() {
		val root = WordListReader.parse("ch {\nword;t\n} \t\n", "c")
		assertEquals("word", root.children.single().words.single().name)

		val error = assertThrows(WordListParseException::class.java) {
			WordListReader.parse("ch {\n} word;translation\n", "c")
		}
		assertEquals(2, error.lineNumber)
		assertEquals(2, error.problemIndex)
		assertEquals(WordListProblem.CHAPTER_BRACES, error.problem)
		assertTrue("tail text stays for the user to fix", error.repairs.isEmpty())
	}

	private fun ItemKind.kindPrefix() = when (this) {
		ItemKind.CHAPTER -> "c:"
		ItemKind.WORD -> "w:"
		ItemKind.TRANSLATION -> "t:"
		else -> "n:"
	}
}
