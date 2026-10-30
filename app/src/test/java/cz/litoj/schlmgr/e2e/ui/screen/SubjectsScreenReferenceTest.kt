package cz.litoj.schlmgr.e2e.ui.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
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
 * - a reference row carries its jump icon wherever it shows, browse included.
 * - the jump opens the home of the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectsScreenReferenceTest {

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

	@Test
	fun `a browse reference row jumps to its home`() {
		val a = ItemRepository.createSubject("A", null)
		val c1 = ItemRepository.createChapter(a.id, "c1", null)
		val b = ItemRepository.createSubject("B", null)
		val w1 = ItemRepository.createWord(b.id, "w1", null, listOf("t1" to null))
		assertTrue(ItemRepository.reference(w1.id, c1.id))

		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		rule.setContent { SubjectsScreen(model, {}, {}, {}) }
		rule.waitForIdle()
		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()
		rule.onNodeWithText("c1").performClick()
		rule.waitForIdle()

		rule.onNodeWithContentDescription("Go to location").performClick()
		rule.waitForIdle()

		assertEquals("the jump opened the home of the row",
			listOf("B"), model.history.path.map { it.name })
		assertEquals(listOf("w1"), model.items().map { it.item.name })
	}
}
