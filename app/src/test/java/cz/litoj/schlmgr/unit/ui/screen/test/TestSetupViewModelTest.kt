package cz.litoj.schlmgr.unit.ui.screen.test

import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.app.UserMessages.UiMessage
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestSetupViewModelTest {

	private val testSetup get() = TestSetupState.shared
	private lateinit var settings: Settings
	private lateinit var userMessages: UserMessages

	@After
	fun resetState() {
		testSetup.reset()
		testSetup.childrenLookup = ItemRepository::children
	}

	/** The doors refresh the counts, which read the database. */
	@Before
	fun fakeTree() {
		val app = RuntimeEnvironment.getApplication()
		settings = Settings(app)
		userMessages = UserMessages(app)
		testSetup.childrenLookup = { emptyList() }
	}

	@Test
	fun `start without sources sends nothing`() = runBlocking {
		assertFalse(testSetup.hasSources())
		val model = TestSetupViewModel(settings = settings, userMessages = userMessages)

		model.startTest()

		assertNull("no request left the channel",
			withTimeoutOrNull(100) { model.requests.first() })
	}

	@Test
	fun `start with sources opens the running test`() = runBlocking {
		testSetup.addPicked(listOf(
			testSetup.seedOf(ItemRow(DbItem(id = 1, name = "subject", description = "", type = ItemKind.CHAPTER),
				null, 1))))
		assertTrue(testSetup.hasSources())
		val model = TestSetupViewModel(settings = settings, userMessages = userMessages)
		val notice = async(start = CoroutineStart.UNDISPATCHED) { model.requests.first() }

		model.startTest()

		withTimeout(1_000) { assertEquals(TestSetupViewModel.Request.StartTest, notice.await()) }
	}

	private fun item(id: Int) =
		DbItem(id = id, name = "i$id", description = "", type = ItemKind.CHAPTER)

	private fun pick(chain: List<DbItem>) {
		testSetup.addPicked(listOf(testSetup.seedOf(
			ItemRow.ofSearchHit(chain.last(), chain.dropLast(1), 1, emptyList()))))
	}

	/** Runs [action] and returns the message it reported. */
	private suspend fun reported(action: () -> Unit): UiMessage = coroutineScope {
		val message = async(start = CoroutineStart.UNDISPATCHED) { userMessages.messages.first() }
		action()
		withTimeout(1_000) { message.await() }
	}

	@Test
	fun `a removal offers its undo, which brings the source back`() = runBlocking {
		val chain = listOf(item(1), item(2))
		pick(chain)
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings, userMessages = userMessages)

		val message = reported { model.removeSource(chain) } as UiMessage.Undoable

		assertFalse(testSetup.hasSources())
		assertEquals(UiNotice.SourceRemoved("i2"), message.notice)
		message.onUndo()
		assertEquals(listOf(chain), testSetup.list)
	}

	@Test
	fun `a refused undo says which subject is picked`() = runBlocking {
		val chain = listOf(item(1), item(2))
		pick(chain)
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings, userMessages = userMessages)
		val message = reported { model.removeSource(chain) } as UiMessage.Undoable
		pick(listOf(item(3), item(4)))

		val notice = reported { message.onUndo() }

		assertEquals(UiMessage.Notice(UiNotice.OneSubjectOnly("i3")), notice)
	}

	@Test
	fun `a repeated removal tap reports nothing`() = runBlocking {
		val chain = listOf(item(1), item(2))
		val model = TestSetupViewModel(Dispatchers.Unconfined, settings = settings, userMessages = userMessages)
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeoutOrNull(100) { userMessages.messages.first() }
		}

		model.removeSource(chain)

		assertNull(message.await())
	}
}
