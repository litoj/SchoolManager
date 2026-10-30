package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - a seeded run shows the picked word, the whole main path of the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestActivityTest {

	@get:Rule
	val rule = createAndroidComposeRule<TestActivity>()

	@Test
	fun `a launched run shows the picked word`() {
		rule.waitForIdle()
		assertTrue(TestRunState.items.isNotEmpty())
		// the translations prompt by default, see the screen
		rule.onNodeWithText("dům").assertExists()
	}

	companion object {

		private lateinit var db: ItemDatabase

		@JvmStatic
		@BeforeClass
		fun seed() {
			// the class setup runs before the activity launches, so the run finds its data
			GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
			db = Room.inMemoryDatabaseBuilder(
				RuntimeEnvironment.getApplication(), ItemDatabase::class.java,
			).allowMainThreadQueries().build()
			ItemRepository.initForTest(db)
			ItemRepository.ensureRoot()
			val a = ItemRepository.createSubject("A", null)
			val c1 = ItemRepository.createChapter(a.id, "c1", null)
			ItemRepository.createWord(c1.id, "house", null, listOf("dům" to null))
			val word = ItemRow(ItemRepository.children(c1.id).single(), c1.id, 1)
			TestSetupState.addPicked(listOf(TestSetupState.seedOf(word)))
		}

		@JvmStatic
		@AfterClass
		fun clean() {
			TestRunState.reset()
			db.close()
		}
	}
}
