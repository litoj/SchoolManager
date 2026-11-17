package cz.litoj.schlmgr.e2e.ui.screen.test

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.TestSetupScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
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
 * - the sources line carries the clear action, which empties the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestSetupScreenClearTest {

	@get:Rule
	val rule = createComposeRule()

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		TestSetupState.reset()
		TestSetupState.childrenLookup = { emptyList() }
	}

	@After
	fun tearDown() {
		TestSetupState.reset()
		TestSetupState.childrenLookup = ItemRepository::children
	}

	private fun label(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	@Test
	fun `the clear action on the sources line empties the list`() {
		val model = TestSetupViewModel()
		rule.setContent { TestSetupScreen(model, openPicker = {}, openDrawer = {}) }
		rule.waitForIdle()

		val subject = DbItem(id = 1, name = "A", description = "", type = ItemKind.CHAPTER)
		val chapter = DbItem(id = 2, name = "B", description = "", type = ItemKind.CHAPTER)
		rule.runOnIdle {
			TestSetupState.addPicked(listOf(TestSetupState.seedOf(
				ItemRow.ofSearchHit(chapter, listOf(subject), 1, emptyList()))))
		}
		rule.waitForIdle()
		rule.onNodeWithText("B").assertExists()
		rule.onNodeWithText(label(R.string.test_clear_sources)).assertExists()

		rule.onNodeWithText(label(R.string.test_clear_sources)).performClick()
		rule.waitForIdle()

		rule.onNodeWithText("B").assertDoesNotExist()
		rule.onNodeWithText(label(R.string.test_clear_sources)).assertDoesNotExist()
	}
}
