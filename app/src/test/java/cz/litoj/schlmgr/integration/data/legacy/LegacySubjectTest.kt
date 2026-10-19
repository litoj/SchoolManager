package cz.litoj.schlmgr.integration.data.legacy

import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.Progress
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.legacy.LegacyFileSource
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Subject-directory import ([ItemRepository.import] with a
 * [LegacySubjectImporter]) on a real in-memory database:
 * - the tree assembles from its split files — a save chapter's node marks its place and carries its file's content.
 * - chapters nest and unlinked files are skipped.
 * - a word re-encountered in another chapter reuses its row.
 * - a persisted `Reference` node becomes a reference link to the item its origin path names.
 * - a failed import leaves nothing behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacySubjectTest {

	private lateinit var db: ItemDatabase
	private lateinit var root: File

	@Before
	fun setUp() {
		val app = RuntimeEnvironment.getApplication()
		GlobalDependencies.appContext = app
		db = Room.inMemoryDatabaseBuilder(app, ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		root = File(app.cacheDir, "legacy-loader-test").apply {
			deleteRecursively()
			mkdirs()
		}
	}

	@After
	fun tearDown() {
		db.close()
		root.deleteRecursively()
	}

	/** The public import boundary, minus the progress bar. */
	private fun import(dir: File) =
		ItemRepository.import(importerOf(dir), ROOT_ID)

	/** A [LegacyFileSource] over plain files — missing or empty files read as nothing. */
	private fun sourceOf(dir: File) = LegacyFileSource { relativePath ->
		File(dir, relativePath).takeIf { it.isFile && it.length() > 0 }?.readText()
	}

	/** The parsed subject as an import source — the gateway's parse-then-import shape. */
	private fun importerOf(dir: File): LegacySubjectImporter {
		val subject = LegacyJsonLoader.parseSubjectDir(sourceOf(dir), dir.name)
			?: throw IllegalArgumentException("Nothing importable in the directory")
		return LegacySubjectImporter(subject)
	}

	@Test
	fun `a save chapter's file is read where the tree links to it`() {
		val subjectDir = File(root, "AJ").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"AJ","cdrn":[""" +
				"""{"class":"cz.SaveChapter","name":"Lexikologie"}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")
		val chaptersDir = File(subjectDir, "Chapters").apply { mkdirs() }
		File(chaptersDir, "Lexikologie.json").writeText(
			"""{"class":"cz.SaveChapter","name":"Lexikologie","desc":"slova","cdrn":[""" +
				"""{"class":"cz.Word","name":"das Haus","cdrn":[""" +
				"""{"class":"cz.Translation","name":"dům"}]}]}"""
		)
		// a leftover file no node links to — a chapter removed in the old app
		File(chaptersDir, "extra.json").writeText(
			"""{"class":"cz.SaveChapter","name":"Extra","cdrn":[""" +
				"""{"class":"cz.Word","name":"die Schule"}]}"""
		)

		assertTrue(import(subjectDir).topLevel.isNotEmpty())

		val subject = ItemRepository.subjects().single()
		// exactly the chapters main.json links to — nothing extra
		assertEquals(listOf("Lexikologie"), ItemRepository.children(subject.id).map { it.name })
		val chapter = ItemRepository.children(subject.id).single()
		// the description and content come from the file
		assertEquals("slova", chapter.description)
		assertEquals(listOf("das Haus"), ItemRepository.children(chapter.id).map { it.name })
		val word = ItemRepository.children(chapter.id).single()
		assertEquals(listOf("dům"), ItemRepository.translations(word.id).map { it.name })
		assertTrue(ItemRepository.getByName("Extra", ItemKind.CHAPTER).isEmpty())
	}

	@Test
	fun `a word re-encountered in another chapter reuses its row and merges descriptions`() {
		val subjectDir = File(root, "MCH").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"MCH","cdrn":[""" +
				"""{"class":"cz.Chapter","name":"c1","cdrn":[""" +
				"""{"class":"cz.Word","name":"das Haus","desc":"house [a]","cdrn":[""" +
				"""{"class":"cz.Translation","name":"dům"}]}]},""" +
				"""{"class":"cz.Chapter","name":"c2","cdrn":[""" +
				"""{"class":"cz.Word","name":"das Haus","desc":"house [b]","cdrn":[""" +
				"""{"class":"cz.Translation","name":"dům"}]}]}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")

		assertTrue(import(subjectDir).topLevel.isNotEmpty())

		// one row serves both chapters — the legacy registry merge
		val word = ItemRepository.getByName("das Haus", ItemKind.WORD).single()
		val c1 = ItemRepository.getByName("c1", ItemKind.CHAPTER).single()
		val c2 = ItemRepository.getByName("c2", ItemKind.CHAPTER).single()
		assertEquals(listOf("das Haus"), ItemRepository.children(c1.id).map { it.name })
		assertEquals(listOf("das Haus"), ItemRepository.children(c2.id).map { it.name })
		assertEquals("house [a]\nhouse [b]", word.description)
		// the translation is one row linked once, like the word
		assertEquals(listOf("dům"), ItemRepository.translations(word.id).map { it.name })
	}

	@Test
	fun `a typed non-word child under a Word is skipped`() {
		val subjectDir = File(root, "Typed").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"Typed","cdrn":[""" +
				"""{"class":"cz.Word","name":"das Haus","cdrn":[""" +
				"""{"class":"cz.Translation","name":"dům"},""" +
				"""{"class":"cz.Note","name":"junk"},""" +
				"""{"name":"no class at all"},""" +
				"""{"class":"cz.Chapter","name":"nested"}]}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")

		assertTrue(import(subjectDir).topLevel.isNotEmpty())

		// only the translation lands — the note and the nested chapter under
		// a Word are skipped, and the class-less node is the real format's
		// translation — it stays
		val word = ItemRepository.getByName("das Haus", ItemKind.WORD).single()
		assertEquals(listOf("dům", "no class at all"),
			ItemRepository.translations(word.id).map { it.name })
		assertTrue(ItemRepository.getByName("junk", ItemKind.NOTE).isEmpty())
		assertTrue(ItemRepository.getByName("nested", ItemKind.CHAPTER).isEmpty())
	}

	@Test
	fun `a failed import leaves nothing behind`() {
		val subjectDir = File(root, "Broken").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"Broken","cdrn":[""" +
				"""{"class":"cz.Chapter","name":"gone"},""" +
				"""{"class":"cz.SaveChapter","name":"bad"}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")
		val chaptersDir = File(subjectDir, "Chapters").apply { mkdirs() }
		// a truncated file — parsing it throws before anything is written
		File(chaptersDir, "bad.json").writeText("""{"class":"cz.SaveChapter","name":"cut"""")

		assertThrows(IllegalArgumentException::class.java) {
			import(subjectDir)
		}
		assertTrue(ItemRepository.subjects().isEmpty())
		assertTrue(ItemRepository.getByName("gone", ItemKind.CHAPTER).isEmpty())
	}

	@Test
	fun `a save chapter can sit anywhere and nest another one`() {
		val subjectDir = File(root, "Deep").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"Deep","cdrn":[""" +
				"""{"class":"cz.Chapter","name":"c1","cdrn":[""" +
				"""{"class":"cz.SaveChapter","name":"My Vocab"}]}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")
		val chaptersDir = File(subjectDir, "Chapters").apply { mkdirs() }
		// the file itself links to another save chapter — no depth limit
		File(chaptersDir, "My Vocab.json").writeText(
			"""{"class":"cz.SaveChapter","name":"My Vocab","cdrn":[""" +
				"""{"class":"cz.SaveChapter","name":"Core"},""" +
				"""{"class":"cz.Word","name":"das Haus"}]}"""
		)
		File(chaptersDir, "Core.json").writeText(
			"""{"class":"cz.SaveChapter","name":"Core","cdrn":[""" +
				"""{"class":"cz.Word","name":"die Schule"}]}"""
		)

		assertTrue(import(subjectDir).topLevel.isNotEmpty())

		val subject = ItemRepository.subjects().single()
		assertEquals(listOf("c1"), ItemRepository.children(subject.id).map { it.name })
		val c1 = ItemRepository.children(subject.id).single()
		assertEquals(listOf("My Vocab"), ItemRepository.children(c1.id).map { it.name })
		val vocab = ItemRepository.children(c1.id).single()
		// the nested save chapter assembled from its own file, next to the word
		assertEquals(listOf("Core", "das Haus"), ItemRepository.children(vocab.id).map { it.name })
		val core = ItemRepository.children(vocab.id).first { it.name == "Core" }
		assertEquals(listOf("die Schule"), ItemRepository.children(core.id).map { it.name })
	}

	@Test
	fun `a persisted reference links the item at its origin path`() {
		val subjectDir = File(root, "Refs").apply { mkdirs() }
		File(subjectDir, "main.json").writeText(
			"""{"class":"cz.MainChapter","name":"Refs","cdrn":[""" +
				"""{"class":"cz.Chapter","name":"home","cdrn":[""" +
				"""{"class":"cz.Word","name":"dog"}]},""" +
				"""{"class":"cz.Chapter","name":"my refs","cdrn":[""" +
				"""{"class":"cz.Reference","name":"dog","origin":"Refs×home"}]}]}"""
		)
		File(subjectDir, "setts.dat").writeText("{}")

		assertTrue(import(subjectDir).topLevel.isNotEmpty())

		val subject = ItemRepository.subjects().single()
		// the bare legacy word (no translations) imported as a note
		val dog = ItemRepository.getByName("dog", ItemKind.NOTE).single()
		val refContainer = ItemRepository.children(subject.id).first { it.name == "my refs" }
		// the item shows in the referencing chapter through a reference link
		assertTrue(ItemRepository.linkIsReference(refContainer.id, dog.id))
		assertEquals(listOf("dog"), ItemRepository.children(refContainer.id).map { it.name })
	}

	@Test
	fun `readText strips a UTF-8 byte order mark`() {
		val file = File(root, "bom.txt")
		file.writeText("\uFEFFchapter {\nword;trl\n}")

		val content = FileText.readText(DocumentFile.fromFile(file).uri)

		// a BOM left in would prefix the first parsed name invisibly
		assertEquals("chapter {\nword;trl\n}", content)
	}

	@Test
	fun `readText reports the bytes it reads`() {
		val file = File(root, "list.txt")
		val text = "chapter {\nword;trl\n}"
		file.writeText(text)
		var reported = 0

		val content = FileText.readText(
			DocumentFile.fromFile(file).uri, Progress { reported += it })

		assertEquals(text, content)
		assertEquals(file.length(), reported.toLong())
	}

	@Test
	fun `writeText reports the bytes it writes`() {
		val target = File(root, "out.txt")
		var reported = 0

		assertTrue(FileText.writeText(
			DocumentFile.fromFile(target).uri, "abcdef", progress = Progress { reported += it }))

		assertEquals(6, reported)
	}
}
