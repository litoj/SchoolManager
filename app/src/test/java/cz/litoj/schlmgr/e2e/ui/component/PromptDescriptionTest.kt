package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestTaskModel
import cz.litoj.schlmgr.ui.component.SharedPromptRow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the prompt describes the shown side, a hidden answer keeps its description to itself.
 * - each shown name carries its own description line, like the explorer row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PromptDescriptionTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun seed() {
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
		).allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		TestEngine.applyReversed(false)
		db.close()
		ItemRepository.resetForTest()
	}

	private fun prompt(reversed: Boolean) {
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val word = ItemRepository.createWord(
			chapter.id, "Haus", "word note\nword more",
			listOf("house" to "noun: building", "roof" to "noun: top"),
		)
		TestEngine.applyReversed(reversed)
		val task = TestTaskModel(listOf(TestItemModel(TestEntry(word, listOf(subject, chapter, word)))), 0)
		rule.setContent {
			SharedPromptRow(task, -1, hashMapOf(), false, rememberLazyListState(), {})
		}
	}

	@Test
	fun `each shown translation carries its own description line`() {
		prompt(reversed = false)

		rule.onNodeWithText("house").assertExists()
		rule.onNodeWithText("noun: building").assertExists()
		rule.onNodeWithText("roof").assertExists()
		rule.onNodeWithText("noun: top").assertExists()
		rule.onNodeWithText("word note").assertDoesNotExist()
	}

	@Test
	fun `the reversed prompt describes the word`() {
		prompt(reversed = true)

		rule.onNodeWithText("Haus").assertExists()
		rule.onNodeWithText("word note").assertExists()
		rule.onNodeWithText("word more").assertDoesNotExist()
		rule.onNodeWithText("noun: building").assertDoesNotExist()
	}
}
