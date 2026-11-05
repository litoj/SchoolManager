package cz.litoj.schlmgr.integration.data.db

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Created and edited text has no edge spaces, whichever item kind it carries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemCreationTrimTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `every create path trims names and descriptions`() {
		val subject = ItemRepository.createSubject(" s ", " sd ")
		assertEquals("s", subject.name)
		assertEquals("sd", subject.description)
		val chapter = ItemRepository.createChapter(subject.id, " c ", " cd ")
		assertEquals("c", chapter.name)
		assertEquals("cd", chapter.description)
		val note = ItemRepository.createNote(chapter.id, " n ", " nd ")
		assertEquals("n", note.name)
		assertEquals("nd", note.description)
		val word = ItemRepository.createWord(chapter.id, " w ", " wd ", listOf(" t " to " td "))
		assertEquals("w", word.name)
		assertEquals("wd", word.description)
		val translation = ItemRepository.translations(word.id).single()
		assertEquals("t", translation.name)
		assertEquals("td", translation.description)
	}

	@Test
	fun `description edits trim their new text`() {
		val subject = ItemRepository.createSubject("s", null)

		ItemRepository.setDescription(subject.id, " outer ")

		assertEquals("outer", ItemRepository.get(subject.id)!!.description)
	}
}
