package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The explorer top bar as the way out of a transient mode.
 * - a selection and an armed move both turn the nav into the cancel arrow.
 * - the armed move reads on the top strip, its actions split the bottom strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenNavTest {

	@get:Rule
	val rule = createComposeRule()

	private lateinit var db: ItemDatabase
	private var chapterId = 0

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		chapterId = c1.id
		ItemRepository.createWord(c1.id, "xyz", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	private fun text(res: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(res, *args)

	private fun selectRow(label: String) {
		rule.onNodeWithText(label).performTouchInput {
			down(center)
			advanceEventTime(1_000)
			up()
		}
		rule.waitForIdle()
	}

	private fun screen(model: MainExplorerViewModel) {
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
	}

	@Test
	fun `a blocked paste keeps its place and refuses the tap`() {
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		screen(model)
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		selectRow("c1")
		rule.onNodeWithText(text(R.string.cut)).performClick()
		rule.waitForIdle()
		rule.runOnIdle { assertTrue("the cut armed a move", model.pasteArmed) }

		// the armed row itself is the one container the paste refuses
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()
		rule.runOnIdle { assertEquals(R.string.fail_paste_inside, model.pasteBlockedReason) }

		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			rule.onNodeWithText(text(R.string.paste)).performClick()
			rule.waitForIdle()
			withTimeout(1_000) {
				assertEquals(
					GlobalDependencies.UiMessage.Notice(
						UiNotice.ActionUnavailable(R.string.fail_paste_inside)),
					notice.await())
			}
		}

		rule.runOnIdle {
			assertTrue("the muted paste refused the tap", model.pasteArmed)
			assertEquals("nothing moved into the armed row",
				listOf("xyz", "w2"), ItemRepository.children(chapterId).map { it.name })
		}
	}
}
