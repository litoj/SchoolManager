package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - blank answers show the no answer text, spaces count as blank.
 * - a typed answer keeps its own text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestResultsPopupNoAnswerTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
		).allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
	}

	private fun showResult(answer: String) {
		val subject = ItemRepository.createSubject("A", null)
		val chapter = ItemRepository.createChapter(subject.id, "c1", null)
		ItemRepository.createWord(chapter.id, "house", null, listOf("dům" to null))
		val model = TestItemModel(TestEntry(ItemRepository.children(chapter.id).single(), emptyList()))
		model.answer = answer
		val popup = TestResultsPopup(listOf(model), 0f)
		rule.setContent { popup.Content() }
	}

	@Test
	fun `an empty answer shows the no answer text`() {
		showResult("")
		rule.onNodeWithText("Your answer: (no answer)").assertExists()
	}

	@Test
	fun `a spaces only answer counts as blank`() {
		showResult("   ")
		rule.onNodeWithText("Your answer: (no answer)").assertExists()
	}

	@Test
	fun `a typed answer keeps its own text`() {
		showResult("hause")

		rule.onNodeWithText("Your answer: hause").assertExists()
		rule.onNodeWithText("Your answer: (no answer)").assertDoesNotExist()
	}
}
