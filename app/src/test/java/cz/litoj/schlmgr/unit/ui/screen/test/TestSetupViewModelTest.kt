package cz.litoj.schlmgr.unit.ui.screen.test

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestSetupViewModelTest {

	@After
	fun resetState() {
		TestSetupState.reset()
		TestSetupState.childrenLookup = ItemRepository::children
	}

	/** The doors refresh the counts, which read the database. */
	@Before
	fun fakeTree() {
		TestSetupState.childrenLookup = { emptyList() }
	}

	@Test
	fun `start without sources sends nothing`() = runBlocking {
		assertFalse(TestSetupState.hasSources())
		val model = TestSetupViewModel()

		model.startTest()

		assertNull("no request left the channel",
			withTimeoutOrNull(100) { model.requests.first() })
	}

	@Test
	fun `start with sources opens the running test`() = runBlocking {
		TestSetupState.addPicked(listOf(
			TestSetupState.seedOf(ItemRow(DbItem(id = 1, name = "subject", description = "", type = ItemKind.CHAPTER),
				null, 1))))
		assertTrue(TestSetupState.hasSources())
		val model = TestSetupViewModel()
		val notice = async(start = CoroutineStart.UNDISPATCHED) { model.requests.first() }

		model.startTest()

		withTimeout(1_000) { assertEquals(TestSetupViewModel.Request.StartTest, notice.await()) }
	}

	private fun item(id: Int) =
		DbItem(id = id, name = "i$id", description = "", type = ItemKind.CHAPTER)

	private fun pick(chain: List<DbItem>) {
		TestSetupState.addPicked(listOf(TestSetupState.seedOf(
			ItemRow.ofSearchHit(chain.last(), chain.dropLast(1), 1, emptyList()))))
	}

	/** Runs [action] and returns the message it reported. */
	private suspend fun reported(action: () -> Unit): GlobalDependencies.UiMessage = coroutineScope {
		val message = async(start = CoroutineStart.UNDISPATCHED) { GlobalDependencies.messages.first() }
		action()
		withTimeout(1_000) { message.await() }
	}

	@Test
	fun `a removal offers its undo, which brings the source back`() = runBlocking {
		val chain = listOf(item(1), item(2))
		pick(chain)
		val model = TestSetupViewModel(Dispatchers.Unconfined)

		val message = reported { model.removeSource(chain) } as GlobalDependencies.UiMessage.Undoable

		assertFalse(TestSetupState.hasSources())
		assertEquals(UiNotice.SourceRemoved("i2"), message.notice)
		message.onUndo()
		assertEquals(listOf(chain), TestSetupState.list)
	}

	@Test
	fun `a refused undo says which subject is picked`() = runBlocking {
		val chain = listOf(item(1), item(2))
		pick(chain)
		val model = TestSetupViewModel(Dispatchers.Unconfined)
		val message = reported { model.removeSource(chain) } as GlobalDependencies.UiMessage.Undoable
		pick(listOf(item(3), item(4)))

		val notice = reported { message.onUndo() }

		assertEquals(GlobalDependencies.UiMessage.Notice(UiNotice.OneSubjectOnly("i3")), notice)
	}

	@Test
	fun `a repeated removal tap reports nothing`() = runBlocking {
		val chain = listOf(item(1), item(2))
		val model = TestSetupViewModel(Dispatchers.Unconfined)
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeoutOrNull(100) { GlobalDependencies.messages.first() }
		}

		model.removeSource(chain)

		assertNull(message.await())
	}
}
