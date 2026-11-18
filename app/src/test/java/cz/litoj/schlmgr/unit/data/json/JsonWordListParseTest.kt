package cz.litoj.schlmgr.unit.data.json

import cz.litoj.schlmgr.data.json.JsonChapter
import cz.litoj.schlmgr.data.json.JsonNote
import cz.litoj.schlmgr.data.json.JsonRef
import cz.litoj.schlmgr.data.json.JsonTranslation
import cz.litoj.schlmgr.data.json.JsonWord
import cz.litoj.schlmgr.data.json.JsonWordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * - no database is touched.
 * - [JsonWordList.writeEntries] is the database-free counterpart of write.
 */
class JsonWordListParseTest {

	@Test
	fun `a single object becomes a list of one subject`() {
		val parsed = JsonWordList.parse(
			"""
			{
			  name: "Subject"
			  desc: "subject description"
			  children:
			  [
			    {
			      name: "word"
			      desc: "word description"
			      s: 3
			      f: 1
			      translations:
			      [
			        {
			          name: "translation"
			          desc: "translation description"
			          s: 2
			          f: 0
			        }
			      ]
			    }
			    {
			      name: "chapter"
			      children: []
			    }
			    {
			      name: "note"
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(1, parsed.size)
		val subject = parsed[0] as JsonChapter
		assertEquals("Subject", subject.name)
		assertEquals("subject description", subject.description)

		// the keys tell the kinds, the list keeps the order
		val word = subject.entries[0] as JsonWord
		assertEquals("word", word.name)
		assertEquals("word description", word.description)
		assertEquals(3, word.passedTests)
		assertEquals(1, word.failedTests)
		assertEquals(1, word.translations.size)

		val trl = word.translations[0]
		assertEquals("translation", trl.name)
		assertEquals("translation description", trl.description)
		assertEquals(2, trl.passedTests)
		assertEquals(0, trl.failedTests)

		val chapter = subject.entries[1] as JsonChapter
		assertEquals("chapter", chapter.name)
		assertTrue(chapter.entries.isEmpty())

		val note = subject.entries[2] as JsonNote
		assertEquals("note", note.name)
	}

	@Test
	fun `an optional field left out or null falls back to the default`() {
		val bodies = mapOf(
			"left out" to "{\n  name: Bare\n  children: []\n}",
			"an explicit null" to "{\n  name: Bare\n  children: []\n  desc: null\n}",
		)

		for ((name, body) in bodies) {
			val subject = JsonWordList.parse(body).single() as JsonChapter
			assertEquals("$name lost its subject", "Bare", subject.name)
			assertNull("$name kept a description", subject.description)
			assertTrue("$name kept an entry", subject.entries.isEmpty())
		}
	}

	@Test
	fun `comments and unquoted keys parse like plain JSON`() {
		val documents = mapOf(
			"comments and unquoted keys" to (
				"""
				// the leading comment
				{
				  /* a block comment */
				  name: "Commented"
				  children:
				  [
				    {
				      name: w
				      translations: []
				    }
				  ]
				}
				""".trimIndent() to "Commented"),
			"plain JSON" to (
				"{\"name\":\"Plain\",\"children\":[{\"name\":\"w\",\"s\":2,\"translations\":[]}]}" to "Plain"),
		)

		for ((name, document) in documents) {
			val subject = JsonWordList.parse(document.first).single() as JsonChapter
			assertEquals("$name lost its subject", document.second, subject.name)
			val word = subject.entries.single() as JsonWord
			assertEquals("$name lost its word", "w", word.name)
		}
	}

	@Test
	fun `a hand-built tree of several roots writes and parses back`() {
		val subject = JsonChapter("Subject", "desc")
		val note = JsonNote("note", "nd")
		val word = JsonWord("word", "wd", 3, 4)
		word.translations.add(JsonTranslation("trl", "td", 5, 6))
		val nested = JsonChapter("nested")
		nested.entries.add(JsonWord("inner", null, 0, 9))
		// a mixed order, which the format must keep through the cycle
		subject.entries.add(note)
		subject.entries.add(word)
		subject.entries.add(nested)
		subject.entries.add(JsonRef(listOf("Subject", "nested", "inner")))
		val bare = JsonWord("Haus", "wd", 1, 2)
		bare.translations.add(JsonTranslation("dům", null, 0, 0))

		val roots = listOf(subject, bare, JsonNote("root note", "nd"))
		val parsed = JsonWordList.parse(JsonWordList.writeEntries(roots))

		assertEquals(3, parsed.size)
		val subject2 = parsed[0] as JsonChapter
		assertEquals("Subject", subject2.name)
		assertEquals("desc", subject2.description)
		assertEquals(
			listOf(JsonNote::class, JsonWord::class, JsonChapter::class, JsonRef::class),
			subject2.entries.map { it::class })
		val note2 = subject2.entries[0] as JsonNote
		assertEquals("note", note2.name)
		assertEquals("nd", note2.description)
		val word2 = subject2.entries[1] as JsonWord
		assertEquals("word", word2.name)
		assertEquals("wd", word2.description)
		assertEquals(3, word2.passedTests)
		assertEquals(4, word2.failedTests)
		val trl2 = word2.translations.single()
		assertEquals("trl", trl2.name)
		assertEquals("td", trl2.description)
		assertEquals(5, trl2.passedTests)
		assertEquals(6, trl2.failedTests)
		val nested2 = subject2.entries[2] as JsonChapter
		assertEquals("nested", nested2.name)
		assertNull(nested2.description)
		assertEquals(9, (nested2.entries.single() as JsonWord).failedTests)
		val ref2 = subject2.entries[3] as JsonRef
		assertEquals(listOf("Subject", "nested", "inner"), ref2.path)

		// a bare root keeps its own kind beside the subject, and alone
		val bare2 = parsed[1] as JsonWord
		assertEquals("Haus", bare2.name)
		assertEquals("wd", bare2.description)
		assertEquals(1, bare2.passedTests)
		assertEquals(listOf("dům"), bare2.translations.map { it.name })
		assertEquals(JsonNote::class, parsed[2]::class)
		assertEquals("Haus", (JsonWordList.parse(JsonWordList.writeEntries(listOf(bare))).single() as JsonWord).name)
	}

	@Test
	fun `every chapter and word writes its kind key`() {
		val outer = JsonChapter("Outer")
		// an empty chapter and a translation-less word, whose always-written keys
		// keep a reader from taking either for a note
		outer.entries.add(JsonChapter("empty"))
		outer.entries.add(JsonWord("word"))

		val text = JsonWordList.writeEntries(listOf(outer))

		assertTrue(text.contains("children"))
		assertTrue(text.contains("translations"))
		assertTrue(text.contains("[]"))
	}

	@Test
	fun `a wrong field type is reported with its path`() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: \"x\"\n  children: \"nope\"\n}")
		}

		assertTrue(error.message!!.contains("root.children"))
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

	@Test
	fun `a node with both children and translations is rejected`() {
		val error = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: x\n  children: []\n  translations: []\n}")
		}

		assertTrue(error.message!!.contains("never both"))
	}

	@Test
	fun `the file is a list of entries, one object a one-item array`() {
		val parsed = JsonWordList.parse(
			"""
			[
			  {
			    name: w
			    translations: []
			  }
			  {
			    name: n
			  }
			  {
			    name: c
			    children: []
			  }
			]
			""".trimIndent()
		)

		// every entry keeps its own kind at the top level, so the list lands in
		// the import target as it is
		assertEquals(3, parsed.size)
		assertTrue(parsed[0] is JsonWord)
		assertTrue(parsed[1] is JsonNote)
		assertTrue(parsed[2] is JsonChapter)

		val single = JsonWordList.parse("{\n  name: n\n}")
		assertEquals(1, single.size)
		assertTrue(single.single() is JsonNote)
	}

	@Test
	fun `the retired format's keys are named, at any level`() {
		// a root exported before the format change, with its top-level subject
		val topLevel = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("[\n  {\n    name: A\n    words: []\n  }\n]")
		}
		assertTrue(topLevel.message!!.contains("retired format"))

		// a nested chapter of the same old file, whose note shape would silently
		// drop its content
		val nested = assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: S\n  children:\n  [\n    {\n      name: c\n      words: []\n    }\n  ]\n}")
		}
		assertTrue(nested.message!!.contains("retired format"))
	}

	@Test
	fun `a reference entry parses its path`() {
		val parsed = JsonWordList.parse(
			"""
			{
			  name: S
			  children:
			  [
			    { ref: ["S", "chapter", "word"] }
			    { ref: ["S"] }
			  ]
			}
			""".trimIndent()
		)

		val ref = (parsed.single() as JsonChapter).entries[0] as JsonRef
		assertEquals(listOf("S", "chapter", "word"), ref.path)
		// the root alone names the root itself
		val rootRef = (parsed.single() as JsonChapter).entries[1] as JsonRef
		assertEquals(listOf("S"), rootRef.path)
	}

	@Test
	fun `a malformed reference entry is rejected`() {
		// a path needs at least the target
		assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: S\n  children: [ { ref: [] } ]\n}")
		}
		// the path is a list of names
		assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: S\n  children: [ { ref: 5 } ]\n}")
		}
		// a reference entry holds only the path
		assertThrows(IllegalArgumentException::class.java) {
			JsonWordList.parse("{\n  name: S\n  children: [ { ref: [\"S\", \"w\"]\n  name: x } ]\n}")
		}
	}
}
