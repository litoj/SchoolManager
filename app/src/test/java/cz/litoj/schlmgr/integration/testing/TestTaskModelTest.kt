package cz.litoj.schlmgr.integration.testing

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTask
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.component.nextTaskRow
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

	@Test
	fun `an expanded task advances the next answer by its field count`() {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val expanded = ItemRepository.createWord(chapter.id, "/I/You smile.", null, listOf("laugh" to null))
		val ordinary = ItemRepository.createWord(chapter.id, "house", null, listOf("dům" to null))
		val models = listOf(expanded, ordinary).map {
			TestItemModel(TestEntry(it, listOf(subject, chapter, it)))
		}
		TestEngine.applyRequireAllVariants(true)
		try {
			val tasks = TestTaskModel.flatten(models, listOf(
				TestTask(listOf(models[0].entry)),
				TestTask(listOf(models[1].entry)),
			))

			assertEquals(listOf(3, 1), tasks.map { it.answerFields.size })
			assertEquals(listOf(0, 3), tasks.map { it.startIndex })
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `next scrolls within a task before advancing to the following task row`() {
		assertEquals(0, nextTaskRow(0, 0, 2))
		assertEquals(1, nextTaskRow(0, 1, 2))
	}
}
