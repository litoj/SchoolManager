package cz.litoj.schlmgr.integration.ui.screen.subjects

import androidx.room.Room
import cz.litoj.schlmgr.app.AppDependencies
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A search run hands its word hit the flip content of the index.
 * - the flip setting itself belongs to unit/ui/viewmodel/SearchHitRowTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchRowPreloadTest {

	private lateinit var db: ItemDatabase
	private lateinit var dependencies: AppDependencies

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.setQueryExecutor(Runnable::run)
			.setTransactionExecutor(Runnable::run)
			.build()
		dependencies = AppDependencies(RuntimeEnvironment.getApplication(), db)
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `a search run hands its word hit the preloaded translation`() {
		val a = ItemRepository.createSubject("A", null)
		val c = ItemRepository.createChapter(a.id, "c1", null, 0)
		ItemRepository.createWord(c.id, "w1", null, listOf("t1" to null))
		// the repository reads run inline on an unconfined dispatcher, so the
		// run finishes before the assertions
		val model = MainExplorerViewModel(
			NavigationHistory(), Dispatchers.Unconfined,
			graphs = dependencies.graphQueries,
			editor = dependencies.itemEditor,
			itemTransferService = dependencies.itemTransfer,
			flipper = dependencies.flipper,
			settings = dependencies.settings,
			userMessages = dependencies.userMessages,
		)
		model.onItemClick(model.items().single { it.item.name == "A" })
		model.openSearchField()
		model.onQuerySubmit("w1")

		assertEquals(listOf("t1"), model.items().map { it.toShow })
		assertTrue("the hit row took the preloaded flip", model.items().single().flipped)
	}
}
