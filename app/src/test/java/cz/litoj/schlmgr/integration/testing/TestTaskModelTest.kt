package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTask
import cz.litoj.schlmgr.testing.TestTaskModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The flattened task group keeps answer indices after a merged prompt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestTaskModelTest {

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
	fun `the task after a merged block starts after both answer fields`() {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val words = listOf("hund", "hond", "hunt").map {
			ItemRepository.createWord(chapter.id, it, null, listOf("pes" to null))
		}
		val models = words.map { TestItemModel(TestEntry(it, listOf(subject, chapter, it))) }
		val tasks = TestTaskModel.flatten(models, listOf(
			TestTask(listOf(models[0].entry, models[1].entry)),
			TestTask(listOf(models[2].entry)),
		))

		assertEquals(listOf(0, 2), tasks.map { it.startIndex })
		assertEquals(listOf("hund", "hond"), tasks[0].members.map { it.entry.word.name })
		assertEquals(listOf("hunt"), tasks[1].members.map { it.entry.word.name })
	}
}
