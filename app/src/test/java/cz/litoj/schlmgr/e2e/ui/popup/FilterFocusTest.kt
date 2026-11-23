package cz.litoj.schlmgr.e2e.ui.popup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.label
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Apply returns focus to the field that supplied the filter door. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FilterFocusTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		val subject = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(subject.id, "c1", null)
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
	}

	private fun show() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent {
			SubjectsScreen(model, {}, {}, {})
			DialogHost()
		}
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
	}

	private fun applyTypeFilter() {
		rule.onNodeWithContentDescription("Filter by").performClick()
		val type = RuntimeEnvironment.getApplication().getString(SearchTag.TYPE.label)
		rule.onNodeWithText(type).performClick()
		rule.onNodeWithText("Apply").performClick()
		rule.waitForIdle()
	}

	@Test
	fun `apply from the closed list opens no search field`() {
		show()

		applyTypeFilter()

		rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
		rule.onNodeWithContentDescription("Search").assertExists()
	}

	@Test
	fun `apply from the open field restores its focus`() {
		show()
		rule.onNodeWithContentDescription("Search").performClick()
		rule.onNode(hasSetTextAction()).assertIsFocused()

		applyTypeFilter()

		rule.onNode(hasSetTextAction()).assertIsFocused()
	}
}
