package cz.litoj.schlmgr.integration.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.concurrent.thread

/**
 * The list contracts of [ExplorerViewModel.loadContent], driven by a
 * hand-fed dispatcher — a create that lands while the container's read is
 * in flight keeps its row (the read retries), and a synchronous load
 * cancels a pending read (its publish never runs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MidReadMutationTest {

	/** A dispatcher the test feeds by hand — the read stays queued until [drain]. */
	internal class ManualDispatcher : CoroutineDispatcher() {
		private val queue = ArrayDeque<Runnable>()

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			queue.addLast(block)
		}

		/** Runs every queued block on the calling thread. */
		fun drain() {
			while (queue.isNotEmpty()) queue.removeFirst().run()
		}
	}

	private class ManualLoadViewModel(history: NavigationHistory, io: CoroutineDispatcher) :
		ExplorerViewModel<Nothing>(history, io = io) {
		protected override fun setContent(container: DbItem?) {}
	}

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(
			RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun `a row created while the load reads survives the landing`() {
		val subject = ItemRepository.createSubject("english", null)
		ItemRepository.createChapter(subject.id, "c1", null)
		val io = ManualDispatcher()
		val vm = ManualLoadViewModel(NavigationHistory(), io)

		// the container's read queues on the manual dispatcher
		vm.loadContent(subject)

		// the READ runs on a worker thread, so its continuation queues on
		// the main looper instead of running inline — the create can land
		// between the read and its publish, exactly the race the retry
		// exists for
		thread { io.drain() }.join()

		// the create lands mid-read, in commitNewItem's order — the database
		// row first, then the UI row
		val created = ItemRepository.createChapter(subject.id, "c2", null)
		vm.addItem(ItemRow(created, subject.id, 2, false))

		// the continuation runs — its snapshot predates the create, so the
		// read retries and lands the fresh database state
		shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()
		io.drain()
		shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()

		assertEquals(listOf("c1", "c2"),
			vm.listState.value.items.map { it.payload!!.item.name })
	}

	@Test
	fun `a synchronous load cancels a pending navigation read`() {
		val subject = ItemRepository.createSubject("english", null)
		ItemRepository.createChapter(subject.id, "c1", null)
		ItemRepository.createChapter(subject.id, "c2", null)
		val io = ManualDispatcher()
		val vm = ManualLoadViewModel(NavigationHistory(), io)

		// the container's read queues on the manual dispatcher
		vm.loadContent(subject)

		// the synchronous search load replaces the list and cancels the job
		vm.load(ArrayList(), isSearch = true)
		assertEquals(0, vm.listState.value.items.size)

		// the queued read runs — the cancelled job publishes nothing
		io.drain()
		shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()

		assertEquals("the late navigation read did not land", 0,
			vm.listState.value.items.size)
	}
}
