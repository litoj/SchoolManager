package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenTopPullTest {

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
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun show(): MainExplorerViewModel {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent {
			SubjectsScreen(model, {}, {}, {})
		}
		rule.waitForIdle()
		return model
	}

	private fun pullDown(name: String) {
		val b = rule.onNodeWithText(name).getBoundsInRoot()
		val px = with(rule.density) { ((b.left + b.right) / 2).toPx() }
		val y = with(rule.density) { ((b.top + b.bottom) / 2).toPx() }
		val by = with(rule.density) { 200.dp.toPx() }
		rule.onRoot().performTouchInput {
			down(Offset(px, y))
			// one step past the slop, then the rest is the pull itself
			moveTo(Offset(px, y + 10f))
			moveTo(Offset(px, y + by))
			up()
		}
		rule.waitForIdle()
	}

	@Test
	fun `a swipe down at the top opens the search field`() {
		val model = show()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()

		pullDown("c1")

		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
		}
		rule.onNode(hasSetTextAction()).assertExists()
	}

	/** A subject with enough rows to scroll, because the pull needs a list that leaves its top. */
	private fun showLong(): MainExplorerViewModel {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "c1", null)
		(2..12).forEach { i ->
			ItemRepository.createWord(a.id, "w$i", null, listOf("t$i" to null))
		}
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent {
			SubjectsScreen(model, {}, {}, {})
		}
		rule.waitForIdle()
		return model
	}

	@Test
	fun `a swipe that scrolls up past the top keeps the field closed`() {
		val model = showLong()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onRoot().performTouchInput { swipeUp() }
		rule.waitForIdle()

		// one gesture from mid-list drags the list back to its top and past it
		rule.onRoot().performTouchInput {
			val c = center
			down(c)
			// past the slop, then all the way to the top and the pull beyond it
			moveTo(Offset(c.x, c.y + 10f))
			moveTo(Offset(c.x, c.y + 800f))
			up()
		}
		rule.waitForIdle()

		rule.runOnIdle {
			assertEquals("the gesture began mid-list — no pull", SearchUi.OFF, model.state.value.searchUi)
		}

		pullDown("c1")

		rule.runOnIdle {
			assertEquals(SearchUi.TYPING, model.state.value.searchUi)
		}
	}

	@Test
	fun `a swipe down at the root keeps the field closed`() {
		val model = show()

		pullDown("A")

		rule.runOnIdle {
			assertEquals(SearchUi.OFF, model.state.value.searchUi)
		}
	}
}
