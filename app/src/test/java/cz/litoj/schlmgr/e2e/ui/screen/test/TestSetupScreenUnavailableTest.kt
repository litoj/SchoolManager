package cz.litoj.schlmgr.e2e.ui.screen.test

import androidx.compose.foundation.layout.Box
import androidx.compose.material.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - an unavailable action keeps its place and explains the tap.
 * - the add source tile survives a fully picked hierarchy.
 * - Start survives an empty source list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestSetupScreenUnavailableTest {

	@get:Rule
	val rule = createComposeRule()

	private var opened = false

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

	/** The screen with the shell snackbar, since a muted tap reports through it. */
	private fun showScreen(model: TestSetupViewModel) {
		rule.setContent {
			val snackbar = messageSnackbarHost()
			Box {
				TestSetupScreen(model, openPicker = { opened = true }, openDrawer = {})
				SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
			}
		}
		rule.waitForIdle()
	}

	private fun label(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	private fun awaitText(text: String) {
		rule.waitUntil(2_000) {
			rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun `the add source tile stays and explains a fully picked hierarchy`() {
		val model = TestSetupViewModel()
		showScreen(model)
		val add = label(R.string.test_add)
		rule.onNodeWithText(add).assertIsDisplayed()

		// a whole subject source leaves nothing left to add
		val subject = DbItem(id = 1, name = "A", description = "", type = ItemKind.CHAPTER)
		rule.runOnIdle {
			TestSetupState.addPicked(listOf(TestSetupState.seedOf(
				ItemRow.ofSearchHit(subject, emptyList(), 1, emptyList()))))
		}
		rule.waitForIdle()
		rule.onNodeWithText(add).assertIsDisplayed()

		rule.onNodeWithText(add).performClick()
		rule.waitForIdle()
		assertFalse("the muted tile never opens the picker", opened)
		awaitText(label(R.string.unavailable_add_source_full))
	}

	@Test
	fun `start without sources stays and explains the tap`() {
		showScreen(TestSetupViewModel())

		rule.onNodeWithText(label(R.string.start)).performClick()
		rule.waitForIdle()
		awaitText(label(R.string.test_start_blocked))
	}
}
