package cz.litoj.schlmgr.integration.ui.viewmodel

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
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
 * - the display of a word follows the defFlip static and the flip state.
 * - a fresh read keeps the side the row stands on.
 * - a non-word row never flips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemRowDisplayTest {

	private lateinit var db: ItemDatabase
	private val savedParse = ItemRow.parse
	private val savedDefFlip = ItemRow.defFlip

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		// raw names, because the join behaviour of the parser has its own unit test
		setRowOptions(parse = false)
	}

	@After
	fun tearDown() {
		setRowOptions(parse = savedParse)
		setRowOptions(defFlip = savedDefFlip)
		db.close()
		ItemRepository.resetForTest()
	}

	private fun wordWithTranslations(): Pair<Int, DbItem> {
		val subject = ItemRepository.createSubject("subject", null)
		val word = ItemRepository.createWord(
			subject.id, "das Haus", "noun", listOf("house" to "dwell", "home" to null))
		return subject.id to word
	}

	private fun translations(wordId: Int) = ItemRepository.translations(wordId)

	@Test
	fun `the stored flip decides how a word row starts`() {
		val cases = mapOf(true to "house\nhome", false to "das Haus")

		for ((on, shown) in cases) {
			setRowOptions(defFlip = on)
			val (parentId, word) = wordWithTranslations()

			val row = ItemRow.ofBrowse(word, parentId, 1, emptyList(), translations(word.id))

			assertEquals("defFlip $on starts on $shown", on, row.flipped)
			assertEquals("defFlip $on shows $shown", shown, row.toShow)
			if (on) {
				assertEquals("no joint description, the lines carry their own", "", row.info)
				assertEquals(listOf("dwell", ""), row.translationLines.map { it.description })
			} else {
				assertEquals("noun", row.info)
				assertTrue(row.translationLines.isEmpty())
			}
		}
	}

	@Test
	fun `flip toggles the display both ways`() {
		setRowOptions(defFlip = false)
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)

		row.applyFlip(translations(word.id))

		assertTrue(row.flipped)
		assertEquals("house\nhome", row.toShow)
		assertEquals("no joint description, the lines carry their own", "", row.info)
		assertEquals(2, row.translationLines.size)

		row.applyFlip(translations(word.id))

		assertFalse(row.flipped)
		assertEquals("das Haus", row.toShow)
		assertEquals("noun", row.info)
		assertTrue(row.translationLines.isEmpty())
	}

	@Test
	fun `a fresh read keeps the side the row stands on`() {
		val cases = listOf(true, false)

		for (on in cases) {
			setRowOptions(defFlip = on)
			val (parentId, word) = wordWithTranslations()
			val row = ItemRow.ofBrowse(word, parentId, 1, emptyList(), translations(word.id))
			ItemRepository.setDescription(word.id, "changed")

			// the io read of production, landed on main as applyFresh does
			row.applyFresh(ItemRepository.get(word.id), translations(word.id))

			assertEquals("a fresh read keeps the flipped row flipped", on, row.flipped)
			assertEquals("the fresh name reaches the unflipped row",
				if (on) "house\nhome" else "das Haus", row.toShow)
			assertEquals("the fresh description reaches the name side",
				if (on) "" else "changed", row.info)
		}
	}

	@Test
	fun `a chapter row shows its description and ignores flips`() {
		val subject = ItemRepository.createSubject("subject", null)
		val chapter = ItemRepository.createChapter(subject.id, "chapter", "chapter description")
		val row = ItemRow(chapter, subject.id, 1)

		assertFalse(row.flipped)
		assertEquals("chapter", row.toShow)
		assertEquals("chapter description", row.info)

		row.applyFlip(emptyList())

		assertFalse(row.flipped)
		assertEquals("chapter", row.toShow)
	}
}
