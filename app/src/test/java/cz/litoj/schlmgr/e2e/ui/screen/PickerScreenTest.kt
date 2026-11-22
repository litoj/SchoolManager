package cz.litoj.schlmgr.e2e.ui.screen

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The picker chrome against its gathered picks.
 * - the select strip shows only while rows are picked, in any folder.
 * - select all picks every row of the folder.
 * - a tap keeps its meaning while picking, so the picks can span folders.
 * - the heading names the task at every level, the crumbs name the location.
 */
@RunWith(RobolectricTestRunner::class)
// a phone wide enough for every list icon
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PickerScreenTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		ItemRepository.createSubject("A", null)
		ItemRepository.createSubject("B", null)
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	private fun text(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	/** Long-presses a row, which picks it like the explorer long-press. */
	private fun selectTheRow() {
		rule.mainClock.autoAdvance = false
		rule.onNodeWithText("A").performTouchInput {
			down(center)
			advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
		}
		settle()
		rule.onNodeWithText("A").performTouchInput { up() }
		rule.mainClock.autoAdvance = true
		rule.waitForIdle()
	}

	/** Taps the select-all icon of the top bar. */
	private fun tapSelectAll() {
		rule.onNodeWithContentDescription(text(R.string.select_all)).performClick()
		rule.waitForIdle()
	}

	/** Advances the clock by hand, so the long-press fires while the finger is down. */
	private fun settle() {
		rule.mainClock.advanceTimeBy(1000)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
		rule.waitForIdle()
	}

	@Test
	fun `the caller names the pick`() {
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}, R.string.action_reference) }
		rule.waitForIdle()

		rule.onNodeWithText(text(R.string.action_reference)).assertExists()
		rule.onNodeWithText(text(R.string.test_add)).assertDoesNotExist()
	}

	@Test
	fun `the strip follows the picks and select all picks all`() {
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}) }
		rule.waitForIdle()

		rule.onNodeWithText(text(R.string.select)).assertDoesNotExist()

		selectTheRow()
		rule.onNodeWithText(text(R.string.select)).assertExists()
		rule.onNodeWithText(text(R.string.objects_cancel)).assertExists()

		tapSelectAll()
		rule.runOnIdle { assertTrue(model.items().all { it.batched }) }

		tapSelectAll()
		rule.runOnIdle { assertFalse(model.items().any { it.batched }) }
		rule.onNodeWithText(text(R.string.select)).assertDoesNotExist()
	}

	@Test
	fun `a tap while picking opens the folder and the strip stays`() {
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}) }
		rule.waitForIdle()

		selectTheRow()

		rule.onNodeWithText("B").performClick()
		rule.waitForIdle()
		rule.runOnIdle {
			assertTrue("the tap opened the folder", model.history.path.single().name == "B")
			assertTrue("the pick of the left folder stays", model.batchRows().single().item.name == "A")
		}
		rule.onNodeWithText(text(R.string.select)).assertExists()
	}
}
