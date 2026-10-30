package cz.litoj.schlmgr.integration.ui.viewmodel

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
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
		ItemRow.parse = false
	}

	@After
	fun tearDown() {
		ItemRow.parse = savedParse
		ItemRow.defFlip = savedDefFlip
		db.close()
		ItemRepository.resetForTest()
	}

	private fun wordWithTranslations(): Pair<Int, DbItem> {
		val subject = ItemRepository.createSubject("subject", null)
		val word = ItemRepository.createWord(
			subject.id, "das Haus", "noun", listOf("house" to "dwell", "home" to null))
		return subject.id to word
	}

	@Test
	fun `a word row starts flipped when defFlip is on`() {
		ItemRow.defFlip = true
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)
		assertTrue(row.flipped)
		assertEquals("house\nhome", row.toShow)
		assertEquals("no joint description, the lines carry their own", "", row.info)
		assertEquals(listOf("dwell", ""), row.translationLines.map { it.description })
	}

	@Test
	fun `a word row starts unflipped when defFlip is off`() {
		ItemRow.defFlip = false
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)
		assertFalse(row.flipped)
		assertEquals("das Haus", row.toShow)
		assertEquals("noun", row.info)
		assertTrue(row.translationLines.isEmpty())
	}

	@Test
	fun `flip toggles the display both ways`() {
		ItemRow.defFlip = false
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)

		row.flip()
		assertTrue(row.flipped)
		assertEquals("house\nhome", row.toShow)
		assertEquals("no joint description, the lines carry their own", "", row.info)
		assertEquals(2, row.translationLines.size)

		row.flip()
		assertFalse(row.flipped)
		assertEquals("das Haus", row.toShow)
		assertEquals("noun", row.info)
		assertTrue(row.translationLines.isEmpty())
	}

	@Test
	fun `update refreshes the unflipped display from the database`() {
		ItemRow.defFlip = false
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)

		ItemRepository.setDescription(word.id, "changed")
		row.update()

		assertFalse(row.flipped)
		assertEquals("das Haus", row.toShow)
		assertEquals("changed", row.info)
	}

	@Test
	fun `update keeps a flipped row flipped`() {
		ItemRow.defFlip = true
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)
		assertTrue(row.flipped)

		ItemRepository.setDescription(word.id, "changed")
		row.update()

		assertTrue(row.flipped)
		assertEquals("house\nhome", row.toShow)
	}

	@Test
	fun `a chapter row shows its description and ignores flips`() {
		val subject = ItemRepository.createSubject("subject", null)
		val chapter = ItemRepository.createChapter(subject.id, "chapter", "chapter description")
		val row = ItemRow(chapter, subject.id, 1)

		assertFalse(row.flipped)
		assertEquals("chapter", row.toShow)
		assertEquals("chapter description", row.info)

		row.flip()
		assertFalse(row.flipped)
		assertEquals("chapter", row.toShow)
	}

	@Test
	fun `setNew re-targets the row onto another word`() {
		ItemRow.defFlip = false
		val (parentId, word) = wordWithTranslations()
		val row = ItemRow(word, parentId, 1)

		val other = ItemRepository.createWord(parentId, "die Schule", null, listOf("school" to null))
		row.setNew(other, parentId)

		assertEquals(other.id, row.item.id)
		assertEquals("die Schule", row.toShow)
		assertFalse(row.flipped)
	}
}
