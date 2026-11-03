package cz.litoj.schlmgr.integration.ui.screen.subjects

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A search run lands its word hits on the search default flip.
 * - the translations come from the index preload, so the flip reads no database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchDefaultFlipTest {

	private lateinit var db: ItemDatabase

	private val searchDefFlipWas = ItemRow.searchDefFlip

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
		ItemRepository.resetForTest()
		setRowOptions(searchDefFlip = searchDefFlipWas)
	}

	private fun searched(): MainExplorerViewModel {
		val a = ItemRepository.createSubject("A", null)
		val c = ItemRepository.createChapter(a.id, "c1", null, 0)
		ItemRepository.createWord(c.id, "w1", null, listOf("t1" to null))
		// the repository reads run inline on an unconfined dispatcher, so the
		// run finishes before the assertions
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		model.onItemClick(model.items().single { it.item.name == "A" })
		model.openSearchField()
		model.onQuerySubmit("w1")
		return model
	}

	@Test
	fun `a word hit shows its translations when the search default flip is on`() {
		val model = searched()

		assertEquals(listOf("t1"), model.items().map { it.toShow })
		assertTrue("the hit row starts flipped", model.items().single().flipped)
	}

	@Test
	fun `a word hit keeps its name when the search default flip is off`() {
		setRowOptions(searchDefFlip = false)

		val model = searched()

		assertEquals(listOf("w1"), model.items().map { it.toShow })
		assertFalse("the hit row stays on its name", model.items().single().flipped)
	}
}
