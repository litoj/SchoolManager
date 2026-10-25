package cz.litoj.schlmgr.e2e.ui.screen

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
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
 * The picker chrome against the selection state, the subjects screen shapes.
 * - the select strip shows only while rows are selected.
 * - the corner button is the select-all of the picker, always present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
		// the saved position of the picker is process-wide, so leave the root for the next class
		PickerViewModel.resetSavedHistory()
		db.close()
	}

	private fun text(res: Int): String = RuntimeEnvironment.getApplication().getString(res)

	/** Long-presses a row, which toggles its selection like the explorer long-press. */
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

	/** Taps the corner button, the select-all slot of the top bar. */
	private fun tapCornerButton() {
		rule.onRoot().performTouchInput {
			val x = width - with(rule.density) { 25.dp.toPx() }
			val y = with(rule.density) { 27.5.dp.toPx() }
			click(Offset(x, y))
		}
		rule.waitForIdle()
	}

	/** Advances the clock by hand, so the long-press fires while the finger is down. */
	private fun settle() {
		rule.mainClock.advanceTimeBy(1000)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
		rule.waitForIdle()
	}

	@Test
	fun `the strip follows the selection and the corner button selects all`() {
		val model = PickerViewModel(Dispatchers.Unconfined)
		rule.setContent { PickerScreen(model, {}) }
		rule.waitForIdle()

		rule.onNodeWithText(text(R.string.select)).assertDoesNotExist()

		selectTheRow()
		rule.onNodeWithText(text(R.string.select)).assertExists()
		rule.onNodeWithText(text(R.string.objects_cancel)).assertExists()

		tapCornerButton()
		rule.runOnIdle { assertTrue(model.items().all { it.selected }) }

		tapCornerButton()
		rule.runOnIdle { assertFalse(model.items().any { it.selected }) }
		rule.onNodeWithText(text(R.string.select)).assertDoesNotExist()
	}
}
