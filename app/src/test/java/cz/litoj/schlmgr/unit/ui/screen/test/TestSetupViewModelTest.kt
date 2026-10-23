package cz.litoj.schlmgr.unit.ui.screen.test

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSetupViewModelTest {

	@After
	fun resetState() {
		TestSetupState.reset()
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
}
