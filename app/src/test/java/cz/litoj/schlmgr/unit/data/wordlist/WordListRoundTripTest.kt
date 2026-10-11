package cz.litoj.schlmgr.unit.data.wordlist

import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import cz.litoj.schlmgr.data.wordlist.WordListReader
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Round-trip tests for the word-list format — what [WordListWriter] writes
 * must parse back into the same item stream ([WordListImporter]). Plain
 * stream items, no database.
 * - every word carries a translation — the format has no line for a
 *   translation-less word.
 */
class WordListRoundTripTest {

	@After
	fun resetSplitter() {
		WordListWriter.setWordTranslationSplitter("=")
	}

	/** Collects the stream while the tree is built — parent first, children after. */
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
	}

	/** Renders [builder]'s stream and parses the text back into a new stream. */
	private fun roundTrip(builder: StreamBuilder): List<StreamItem> =
		WordListImporter("root", WordListWriter.write(builder.items)).items

	@Test
	fun specialCharactersInNamesRoundTrip() {
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
	fun variantSyntaxNamesRoundTrip() {
		val b = StreamBuilder()
		// a `\` in a name is variant syntax, not a literal — it stays raw
		b.word("word\\/syn\\(onym)", b.chapter("subject"), "", "trl" to "")
		assertEquals("word\\/syn\\(onym)",
			roundTrip(b).first { it.item.type == ItemKind.WORD }.item.name)
	}

	@Test
	fun descriptionsRoundTrip() {
		val b = StreamBuilder()
		val w = b.word("word", b.chapter("subject"), "ends with ] and [ inside")
		b.add(StreamItem(DbItem(name = "trl", description = "back\\slash and \\] escape",
			type = ItemKind.TRANSLATION), w))
		val back = roundTrip(b)
		assertEquals("ends with ] and [ inside",
			back.first { it.item.type == ItemKind.WORD }.item.description)
		assertEquals("back\\slash and \\] escape",
			back.first { it.item.type == ItemKind.TRANSLATION }.item.description)
	}

	@Test
	fun multilineDescriptionRoundTrip() {
		val b = StreamBuilder()
		b.word("word", b.chapter("subject"), "first line\nsecond line", "trl" to "")
		assertEquals("first line\nsecond line",
			roundTrip(b).first { it.item.type == ItemKind.WORD }.item.description)
	}

	@Test
	fun nestedChaptersRoundTrip() {
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
	fun multipleContainersRoundTrip() {
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
	fun rootWordAndNoteLinesRoundTrip() {
		val b = StreamBuilder()
		b.word("bare word", null, "word description", "trl" to "trl description")
		b.add(StreamItem(DbItem(name = "bare note", description = "note description",
			type = ItemKind.NOTE), null))
		val back = roundTrip(b)
		// a root word keeps its line and its kind — no empty chapter wrapper
		val word = back.first { it.item.type == ItemKind.WORD }
		assertEquals(null, word.parent)
		assertEquals("bare word", word.item.name)
		assertEquals("word description", word.item.description)
		assertEquals("trl", back.first { it.item.type == ItemKind.TRANSLATION }.item.name)
		val note = back.first { it.item.type == ItemKind.NOTE }
		assertEquals(null, note.parent)
		assertEquals("bare note", note.item.name)
	}

	@Test
	fun customSplitterRoundTrip() {
		WordListWriter.setWordTranslationSplitter(";")
		val b = StreamBuilder()
		b.word("semi;colon", b.chapter("subject"), "", "trl" to "")
		val back = roundTrip(b)
		assertEquals("semi;colon", back.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals("trl", back.first { it.item.type == ItemKind.TRANSLATION }.item.name)
	}

	@Test
	fun aSplitterTheReaderCannotParseIsRefused() {
		WordListWriter.setWordTranslationSplitter("=")
		WordListWriter.setWordTranslationSplitter(":")
		assertEquals("=", WordListWriter.wordTranslationSplitter)
		WordListWriter.setWordTranslationSplitter("a b")
		assertEquals("=", WordListWriter.wordTranslationSplitter)
	}

	@Test
	fun backslashAtNameEndRoundTrips() {
		val b = StreamBuilder()
		b.word("ends\\", b.chapter("subject"), "", "a\\" to "", "b" to "")
		val back = roundTrip(b)
		// the trailing run doubles on the way out and halves on the way
		// back — the exact names survive
		assertEquals("ends\\", back.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals(listOf("a\\", "b"),
			back.filter { it.item.type == ItemKind.TRANSLATION }.map { it.item.name })

		// the second cycle halves what the first doubled — nothing drifts
		val back2 = WordListImporter("subject", WordListWriter.write(back)).items
		assertEquals("ends\\", back2.first { it.item.type == ItemKind.WORD }.item.name)
		assertEquals(listOf("a\\", "b"),
			back2.filter { it.item.type == ItemKind.TRANSLATION }.map { it.item.name })
	}

	@Test
	fun notesRoundTrip() {
		val b = StreamBuilder()
		val subject = b.chapter("subject")
		b.add(StreamItem(DbItem(name = "note", description = "note description",
			type = ItemKind.NOTE), subject))
		b.add(StreamItem(DbItem(name = "plain note", description = "",
			type = ItemKind.NOTE), subject))
		b.add(StreamItem(DbItem(name = "back\\slash note", description = "",
			type = ItemKind.NOTE), subject))
		val back = roundTrip(b)
		val notes = back.filter { it.item.type == ItemKind.NOTE }
		assertEquals(listOf("note", "plain note", "back\\slash note"), notes.map { it.item.name })
		assertEquals("note description", notes[0].item.description)
		assertEquals("subject", notes[0].parent?.item?.name)
	}

	@Test
	fun noteLinesCarryExactlyOneName() {
		// a second separator on a note line is malformed
		try {
			WordListReader.parse("ch {\n=named=again\n}\n", "c")
			fail("a note line with a second separator must fail the parse")
		} catch (e: IllegalArgumentException) {
			// expected
		}
		// a note line without a name is malformed too
		try {
			WordListReader.parse("ch {\n=\n}\n", "c")
			fail("a nameless note line must fail the parse")
		} catch (e: IllegalArgumentException) {
			// expected
		}
	}

	@Test
	fun blankLinesKeepErrorPositions() {
		try {
			WordListReader.parse("ch {\n\n\nword without separator\n}\n", "c")
			fail("a word line without translations must fail the parse")
		} catch (e: IllegalArgumentException) {
			assertTrue("the error names line 4, was: ${e.message}",
				e.message!!.contains("line 4"))
		}
	}

	@Test
	fun crlfChapterOpenerParses() {
		val root = WordListReader.parse("ch {\r\nword;t\r\n}\r\n", "c")
		val ch = root.children.single()
		assertEquals("ch", ch.name)
		assertEquals("word", ch.words.single().name)
	}

	@Test
	fun crlfFilesKeepErrorPositions() {
		try {
			WordListReader.parse("ch {\r\n\r\nword without separator\r\n}\r\n", "c")
			fail("a word line without translations must fail the parse")
		} catch (e: IllegalArgumentException) {
			assertTrue("the error names line 3, was: ${e.message}",
				e.message!!.contains("line 3"))
		}
	}

	@Test
	fun loneCrIsALineEnding() {
		// a lone `\r` ends the line without eating the next character
		val root = WordListReader.parse("ch {\rword;t\r}\r", "c")
		val ch = root.children.single()
		assertEquals("word", ch.words.single().name)
	}

	@Test
	fun aStrayClosingBraceAtTheRootFailsInsteadOfTruncating() {
		try {
			WordListReader.parse("}\nword;t\n", "c")
			fail("a stray brace at the root must fail the parse")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("No chapter is open"))
		}
	}

	@Test
	fun anEmptyWordNameRefusesTheWrite() {
		// a bare splitter line would read back as a note (a silent kind
		// change) or fail the parse
		val word = StreamItem(DbItem(name = "", description = "", type = ItemKind.WORD), null)
		val trl = StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), word)
		try {
			WordListWriter.write(listOf(word, trl))
			fail("an empty word name must refuse the write")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("word-list line"))
		}
	}

	@Test
	fun anEmptyNoteNameRefusesTheWrite() {
		val note = StreamItem(DbItem(name = "", description = "", type = ItemKind.NOTE), null)
		try {
			WordListWriter.write(listOf(note))
			fail("an empty note name must refuse the write")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("word-list line"))
		}
	}

	@Test
	fun aWhitespaceNameRefusesTheWrite() {
		// the reader trims the line — a blank name would read back as a note
		val word = StreamItem(DbItem(name = " ", description = "", type = ItemKind.WORD), null)
		val trl = StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), word)
		try {
			WordListWriter.write(listOf(word, trl))
			fail("a whitespace name must refuse the write")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("word-list line"))
		}
	}

	@Test
	fun aWhitespacePieceAddsNothingAtTheLineEnd() {
		// `t\ ` splits the translations, the blank piece trims to no name —
		// the word keeps its real translation alone
		val root = WordListReader.parse("w;t\\ \n", "c")
		assertEquals(listOf("t"), root.words.single().translations.map { it.name })
	}

	@Test
	fun aWhitespaceWordPieceFailsTheParse() {
		// `word\ ` splits, and the blank piece then meets the description —
		// it must fail, not land a blank-named word the writers refuse
		try {
			WordListReader.parse("word\\ [d]=trl\n", "c")
			fail("a whitespace-only word piece must fail the parse")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("Expected text"))
		}
	}

	@Test
	fun aLoneDividerInAChapterNameFailsInsteadOfTruncating() {
		// `a\b {` splits into the name "a" and the pending text "b" — the
		// chapter must not import as "a" with "b" silently lost
		try {
			WordListReader.parse("a\\b {\nword;t\n}\n", "c")
			fail("a lone divider in a chapter name must fail the parse")
		} catch (e: IllegalArgumentException) {
			assertTrue(e.message!!.contains("divider"))
		}
	}

	@Test
	fun anInterleavedFileKeepsItsLineOrder() {
		// words, notes and chapters interleave in the file — the stream
		// keeps that order, so the import's link positions match the file
		val importer = WordListImporter("c", "ch {\nw1;t1\n= n1\nw2;t2\n}\n")
		val names = importer.items.map { it.item.type.kindPrefix() + it.item.name }
		assertEquals(listOf("c:ch", "w:w1", "t:t1", "n:n1", "w:w2", "t:t2"), names)
	}

	@Test
	fun anOddBackslashRunBeforeASeparatorStaysParseableAndStable() {
		// a stored name with one backslash before an escaped separator has
		// no exact encoding — the write evens the run (one backslash more),
		// the file parses, and the next cycle writes the same text
		fun stream(name: String): List<StreamItem> {
			val word = StreamItem(DbItem(name = name, description = "", type = ItemKind.WORD), null)
			return listOf(word,
				StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), word))
		}
		val text1 = WordListWriter.write(stream("a\\;"))
		val parsed = WordListReader.parse(text1, "c")
		// the run became even — the name carries two backslashes now
		assertEquals("a\\\\;", parsed.words.single().name)

		// the second write reproduces the same text — the cycle is stable
		assertEquals(text1, WordListWriter.write(stream(parsed.words.single().name)))
	}

	@Test
	fun anEvenBackslashRunBeforeASeparatorRoundTripsExactly() {
		val word = StreamItem(DbItem(name = "a\\\\=", description = "", type = ItemKind.WORD), null)
		val items = listOf(word,
			StreamItem(DbItem(name = "t", description = "", type = ItemKind.TRANSLATION), word))
		val parsed = WordListReader.parse(WordListWriter.write(items), "c")
		assertEquals("a\\\\=", parsed.words.single().name)
	}

	private fun ItemKind.kindPrefix() = when (this) {
		ItemKind.CHAPTER -> "c:"
		ItemKind.WORD -> "w:"
		ItemKind.TRANSLATION -> "t:"
		else -> "n:"
	}
}
