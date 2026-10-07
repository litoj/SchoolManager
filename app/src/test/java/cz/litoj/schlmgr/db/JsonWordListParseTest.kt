package cz.litoj.schlmgr.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import cz.litoj.schlmgr.db.io.JsonChapter
import cz.litoj.schlmgr.db.io.JsonTranslation
import cz.litoj.schlmgr.db.io.JsonWord
import cz.litoj.schlmgr.db.io.JsonWordList

/**
 * Pure-JVM tests of the tree JSON (HJSON) codec [JsonWordList] — the parser and
 * the hand-built round trip. No database is touched: [JsonWordList.renderChapters]
 * is the database-free counterpart of `render`.
 */
class JsonWordListParseTest {

	@Test
	fun `a single object becomes a list of one subject`() {
		val parsed = JsonWordList.parse(
			"""
			{
			  name: "Subject"
			  desc: "subject description"
			  s: 51
			  f: 5
			  words: [
			    {
			      name: "word"
			      desc: "word description"
			      s: 3
			      f: 1
			      translations: [
			        {
			          name: "translation"
			          desc: "translation description"
			          s: 2
			          f: 0
			        }
			      ]
			    }
			  ]
			  chapters: [
			    {
			      name: "chapter"
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(1, parsed.size)
		val subject = parsed[0]
		assertEquals("Subject", subject.name)
		assertEquals("subject description", subject.description)
		assertEquals(51, subject.passed)
		assertEquals(5, subject.failed)

		assertEquals(1, subject.words.size)
		val word = subject.words[0]
		assertEquals("word", word.name)
		assertEquals("word description", word.description)
		assertEquals(3, word.passed)
		assertEquals(1, word.failed)
		assertEquals(1, word.translations.size)

		val trl = word.translations[0]
		assertEquals("translation", trl.name)
		assertEquals("translation description", trl.description)
		assertEquals(2, trl.passed)
		assertEquals(0, trl.failed)

		assertEquals(1, subject.chapters.size)
		assertEquals("chapter", subject.chapters[0].name)
	}

	@Test
	fun `a top-level array becomes several subjects`() {
		val parsed = JsonWordList.parse(
			"""
			[
			  {
			    name: "One"
			  }
			  {
			    name: "Two"
			    s: 1
			  }
			]
			""".trimIndent()
		)

		assertEquals(2, parsed.size)
		assertEquals("One", parsed[0].name)
		assertEquals(0, parsed[0].passed)
		assertEquals("Two", parsed[1].name)
		assertEquals(1, parsed[1].passed)
	}

	@Test
	fun `missing optional fields fall back to the defaults`() {
		val parsed = JsonWordList.parse("{\n  name: Bare\n}")

		val subject = parsed.single()
		assertEquals("Bare", subject.name)
		assertNull(subject.description)
		assertEquals(0, subject.passed)
		assertEquals(0, subject.failed)
		assertTrue(subject.words.isEmpty())
		assertTrue(subject.chapters.isEmpty())
	}

	@Test
	fun `an explicit null optional value is treated as missing`() {
		val parsed = JsonWordList.parse("{\n  name: Bare\n  desc: null\n}")

		assertNull(parsed.single().description)
	}

	@Test
	fun `comments and unquoted keys are accepted`() {
		val parsed = JsonWordList.parse(
			"""
			// the leading comment
			{
			  /* a block comment */
			  name: "Commented"
			  s: 7 # an inline comment after a quoted value
			  words: [
			    {
			      name: "w"
			    }
			  ]
			}
			""".trimIndent()
		)

		val subject = parsed.single()
		assertEquals("Commented", subject.name)
		assertEquals(7, subject.passed)
		assertEquals(1, subject.words.size)
		assertEquals("w", subject.words[0].name)
	}

	@Test
	fun `plain JSON is accepted as well`() {
		val parsed = JsonWordList.parse(
			"{\"name\":\"Plain\",\"s\":2,\"words\":[{\"name\":\"w\",\"f\":1}]}"
		)

		val subject = parsed.single()
		assertEquals("Plain", subject.name)
		assertEquals(2, subject.passed)
		assertEquals("w", subject.words.single().name)
		assertEquals(1, subject.words.single().failed)
	}

	@Test
	fun `a hand-built tree survives the render-parse round trip`() {
		val subject = JsonChapter("Subject", "desc", 1, 2)
		val word = JsonWord("word", "wd", 3, 4)
		word.translations.add(JsonTranslation("trl", "td", 5, 6))
		subject.words.add(word)
		val nested = JsonChapter("nested", null, 7, 8)
		nested.words.add(JsonWord("inner", null, 0, 9))
		subject.chapters.add(nested)

		val parsed = JsonWordList.parse(JsonWordList.renderChapters(listOf(subject)))

		assertEquals(1, parsed.size)
		val subject2 = parsed[0]
		assertEquals("Subject", subject2.name)
		assertEquals("desc", subject2.description)
		assertEquals(1, subject2.passed)
		assertEquals(2, subject2.failed)
		val word2 = subject2.words.single()
		assertEquals("word", word2.name)
		assertEquals("wd", word2.description)
		assertEquals(3, word2.passed)
		assertEquals(4, word2.failed)
		val trl2 = word2.translations.single()
		assertEquals("trl", trl2.name)
		assertEquals("td", trl2.description)
		assertEquals(5, trl2.passed)
		assertEquals(6, trl2.failed)
		val nested2 = subject2.chapters.single()
		assertEquals("nested", nested2.name)
		assertNull(nested2.description)
		assertEquals(7, nested2.passed)
		assertEquals(8, nested2.failed)
		assertEquals(9, nested2.words.single().failed)
	}

	@Test
	fun `several chapters render as an array and parse back`() {
		val a = JsonChapter("A", null, 1, 0)
		val b = JsonChapter("B", "bd", 0, 2)

		val parsed = JsonWordList.parse(JsonWordList.renderChapters(listOf(a, b)))

		assertEquals(2, parsed.size)
		assertEquals("A", parsed[0].name)
		assertEquals(1, parsed[0].passed)
		assertEquals("B", parsed[1].name)
		assertEquals("bd", parsed[1].description)
		assertEquals(2, parsed[1].failed)
	}

	@Test
	fun `unset optional fields are omitted from the output`() {
		val text = JsonWordList.renderChapters(listOf(JsonChapter("Bare")))

		assertTrue(text.contains("Bare"))
		assertTrue(!text.contains("desc"))
		assertTrue(!text.contains("s:"))
		assertTrue(!text.contains("f:"))
	}

	@Test
	fun `a wrong field type is reported with its path`() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: \"x\"\n  s: \"nope\"\n}")
		}

		assertTrue(error.message!!.contains("root.s"))
	}

	@Test
	fun `a malformed text is reported with a position`() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: \"x\"\n")
		}

		assertTrue(error.message!!.contains("line"))
	}

	@Test
	fun `a non-object root is rejected`() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("5")
		}

		assertTrue(error.message!!.contains("root"))
	}
}
