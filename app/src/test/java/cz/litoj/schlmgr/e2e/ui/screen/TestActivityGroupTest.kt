package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.component.SharedPromptRow
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** An exact duplicate prompt renders as one block with all its answer fields. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestActivityGroupTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase
	private lateinit var task: TestTaskModel

	@Before
	fun seed() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
		).allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val first = ItemRepository.createWord(chapter.id, "hund", null, listOf("pes" to null))
		val second = ItemRepository.createWord(chapter.id, "hond", null, listOf("pes" to null))
		task = TestTaskModel(
			listOf(
				TestItemModel(TestEntry(first, listOf(subject, chapter, first))),
				TestItemModel(TestEntry(second, listOf(subject, chapter, second))),
			),
			0,
		)
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `name variants each get their own answer line`() {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val word = ItemRepository.createWord(chapter.id, "/I/You smile.", null, listOf("laugh" to null))
		TestEngine.applyRequireAllVariants(true)
		try {
			val task = TestTaskModel(listOf(TestItemModel(TestEntry(word, listOf(subject, chapter, word)))), 0)
			rule.setContent {
				SharedPromptRow(task, -1, hashMapOf(), false, rememberLazyListState(), {})
			}

			assertEquals(3, task.answerFields.size)
			rule.onAllNodes(hasSetTextAction()).assertCountEquals(3)
		} finally {
			TestEngine.applyRequireAllVariants(false)
		}
	}

	@Test
	fun `identical prompts merge into one block with both answer fields`() {
		rule.setContent {
			SharedPromptRow(task, -1, hashMapOf(), false, rememberLazyListState(), {})
		}

		assertEquals(2, task.members.size)
		rule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
		rule.onAllNodesWithText("pes").assertCountEquals(1)
	}

}
