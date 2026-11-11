package cz.litoj.schlmgr.e2e.ui.component

import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.component.ItemList
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.ListEndReveal
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import java.time.Duration
import org.junit.Assert.assertEquals
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
 * - a row the screen already shows whole is revealed by its insert, so the list never moves for it.
 * - the reveal of a row below the screen obeys the phone's animation setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListEndRevealTest {

	@get:Rule
	val rule = createComposeRule()

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	private fun rows(count: Int) = List(count) { i ->
		ItemUiModel("k$i", i + 1, "item ${i + 1}", "", RowKind.SUBJECT, 0, false, selected = false)
	}

	/** The phone's animation scales, a zero scale stands for the animations being off. */
	private fun setAnimationScale(scale: Float) {
		val cr = GlobalDependencies.appContext.contentResolver
		Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
		Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
	}

	/** Appends one row and reports the first visible index and the composed rows of every later frame. */
	private fun append(scale: Float, count: Int): Pair<List<Int>, Int> {
		setAnimationScale(scale)
		var scroll: LazyListState? = null
		var state by mutableStateOf(ItemListState(items = rows(count), maxPosition = count))
		rule.mainClock.autoAdvance = false
		rule.setContent {
			val own = scroll ?: LazyListState().also { scroll = it }
			Box(Modifier.size(400.dp)) {
				ItemList(state, ItemListCallbacks(), Modifier.fillMaxSize(), listState = own)
				ListEndReveal(state, own)
			}
		}
		rule.waitForIdle()
		val list = scroll!!
		state = state.copy(
			items = rows(count + 1),
			maxPosition = count + 1,
			contentStep = state.contentStep + 1,
			scrollToBottom = state.scrollToBottom + 1,
		)
		// the write rides no frame of its own, so the composition needs its notification by hand
		Snapshot.sendApplyNotifications()
		val frames = ArrayList<Int>()
		repeat(10) {
			frame(16)
			frames += list.firstVisibleItemIndex
		}
		return frames to list.layoutInfo.visibleItemsInfo.size
	}

	private fun frame(millis: Long) {
		rule.mainClock.advanceTimeBy(millis)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
	}

	@Test
	fun `a row the screen shows whole moves the list nowhere`() {
		val (frames, shown) = append(1f, 3)

		assertEquals("the list moved: $frames", listOf(0), frames.distinct())
		assertEquals("the appended row joined the screen", 4, shown)
	}

	@Test
	fun `an off animation setting reveals the end without passing a row`() {
		val (frames, _) = append(0f, 30)
		val landed = frames.last()

		assertTrue("the last frame shows the end: $frames", landed > 20)
		assertTrue("the list passed rows on its way: $frames", frames.none { it in 1 until landed })
	}

	@Test
	fun `an on animation setting glides over the rows`() {
		val (frames, _) = append(1f, 30)
		val landed = frames.last()

		assertTrue("the last frame shows the end: $frames", landed > 20)
		assertTrue("the list never glided: $frames", frames.any { it in 1 until landed })
	}
}
