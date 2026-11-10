package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestEntry
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.TestResultsPopup
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the headline is the prompt of the direction, the expected answer is labelled below.
 * - the direction of the run holds after the setting changes.
 * - translations past the description limit stay listed.
 * - a word without translations shows no empty label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestResultsPopupTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	private val savedLimit = ItemRow.descLines

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
		TestEngine.applyReversed(false)
		setRowOptions(descLines = savedLimit)
		db.close()
		ItemRepository.resetForTest()
	}

	private fun seededWord(): DbItem {
		val subject = ItemRepository.createSubject("A", null)
		val chapter = ItemRepository.createChapter(subject.id, "c1", null)
		ItemRepository.createWord(
			chapter.id, "chleba", null, listOf("bread" to null, "roll" to null))
		return ItemRepository.children(chapter.id).single()
	}

	private fun popupOf(word: DbItem): TestResultsPopup {
		val model = TestItemModel(TestEntry(word, listOf(word)))
		model.answer = "a"
		return TestResultsPopup(listOf(model), 0f)
	}

	private fun show(popup: TestResultsPopup) = rule.setContent { popup.Content() }

	private fun assertDefaultRow() {
		rule.onNodeWithText("bread\nroll").assertExists()
		rule.onNodeWithText("Correct: chleba").assertExists()
		rule.onNodeWithText("Your answer: a").assertExists()
	}

	@Test
	fun `a default row heads with the translations and labels the word`() {
		show(popupOf(seededWord()))

		assertDefaultRow()
	}

	@Test
	fun `a reversed row heads with the word and labels the translations`() {
		TestEngine.applyReversed(true)

		show(popupOf(seededWord()))

		rule.onNodeWithText("chleba").assertExists()
		rule.onNodeWithText("Correct: bread\nroll").assertExists()
		rule.onNodeWithText("Your answer: a").assertExists()
	}

	@Test
	fun `the direction of the run holds after the setting changes`() {
		val popup = popupOf(seededWord())

		TestEngine.applyReversed(true)
		show(popup)

		assertDefaultRow()
	}

	@Test
	fun `translations past the description limit stay listed`() {
		setRowOptions(descLines = 1)
		TestEngine.applyReversed(true)

		show(popupOf(seededWord()))

		rule.onNodeWithText("Correct: bread\nroll").assertExists()
	}

	@Test
	fun `result positions follow item order rather than lifetime counts`() {
		val models = listOf(7 to 3, 0 to 1).mapIndexed { index, counters ->
			val word = DbItem(
				id = index + 1, name = "word$index", description = "", type = ItemKind.WORD,
				passedTests = counters.first, failedTests = counters.second,
			)
			TestItemModel(TestEntry(word, listOf(word))).apply { answer = "a" }
		}
		show(TestResultsPopup(models, 0f))

		rule.onNodeWithText("1.").assertExists()
		rule.onNodeWithText("2.").assertExists()
		rule.onNodeWithText("7/10").assertExists()
		rule.onNodeWithText("0/1").assertExists()
		rule.onNodeWithText("10.").assertDoesNotExist()
	}

	@Test
	fun `a correct variant answer still labels the full word`() {
		val subject = ItemRepository.createSubject("B", null)
		val chapter = ItemRepository.createChapter(subject.id, "c1", null)
		val word = ItemRepository.createWord(
			chapter.id, "laufen/rennen", null, listOf("run" to null))
		val model = TestItemModel(TestEntry(word, listOf(word)))
		model.correct = true
		model.answer = "laufen"

		show(TestResultsPopup(listOf(model), 100f))

		rule.onNodeWithText("Correct: laufen/rennen").assertExists()
		rule.onNodeWithText("Your answer: laufen").assertExists()
	}

	@Test
	fun `a word without translations shows no empty label`() {
		TestEngine.applyReversed(true)

		// a mis-anchored import is the only source of such a word, so the creator cannot seed it
		show(popupOf(DbItem(name = "chleba", description = "", type = ItemKind.WORD)))

		rule.onNodeWithText("Correct", substring = true).assertDoesNotExist()
	}
}
