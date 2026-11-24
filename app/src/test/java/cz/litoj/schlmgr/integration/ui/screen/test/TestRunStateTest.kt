package cz.litoj.schlmgr.integration.ui.screen.test

import android.os.Looper
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.CounterService
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.testing.TestItemModel
import cz.litoj.schlmgr.testing.TestRunData
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.test.TestRunState
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.blankAnswerCount
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineScope
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * - the end of the run closes a question that asked about the answers.
 * - no live run holds nothing to ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestRunStateTest {

	private val testSetup get() = TestSetupState.shared

	private inner class FakeHost : TestRunState.Host {
		override val scope = CoroutineScope(Dispatchers.Unconfined)
		override val graphs = GraphQueries(db)
		override val counters = CounterService(db)
		var foreground = true
		var results: List<TestItemModel>? = null
		override fun isAlive() = true
		override fun isForeground() = foreground
		override fun onNoObjects() {}
		override fun onTimeUp() {}
		override fun onResults(items: List<TestItemModel>, ratio: Float) {
			results = items
		}
		override fun onAborted() {}
	}

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		TestRunState.reset()
		testSetup.reset()
		TestEngine.applyReversed(false)
		TestEngine.applyRequireAllVariants(false)
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val word = ItemRepository.createWord(chapter.id, "house", null, listOf("dům" to null))
		val row = ItemRow(word, chapter.id, 1)
		val chain = listOf(subject, chapter, word)
		val entries = runBlocking {
			mapOf(chain to TestRunData.readEntries(listOf(chain), GraphQueries(db)))
		}
		testSetup.addPicked(
			listOf(testSetup.seedOf(row)), mapOf(chapter.id to listOf(subject, chapter)), entries)
		ItemRepository.resetForTest()
		shadowOf(Looper.getMainLooper()).pause()
	}

	@After
	fun tearDown() {
		TestRunState.reset()
		testSetup.reset()
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `a timed submit closes the open question`() {
		val host = FakeHost()
		var acted = 0
		TestRunState.attach(host)
		TestRunState.confirm("Sure?") { acted++ }
		assertTrue(Dialogs.isShowing<ContinuePopup>())

		TestRunState.submit(host)

		assertFalse(Dialogs.isShowing<ContinuePopup>())
		assertEquals(0, acted)
	}

	@Test
	fun `an exit closes the open question`() {
		TestRunState.attach(FakeHost())
		TestRunState.confirm("Sure?") {}

		TestRunState.reset()

		assertFalse(Dialogs.isShowing<ContinuePopup>())
	}

	@Test
	fun `blank answers include fields beyond the item count`() {
		assertEquals(1, blankAnswerCount(mapOf(0 to "house"), 2))
	}

	private fun await(landed: () -> Boolean) {
		var remaining = 100
		while (!landed() && remaining-- > 0) {
			Thread.sleep(10)
			shadowOf(Looper.getMainLooper()).idle()
		}
		assertTrue("the state landing arrived", landed())
	}

	private fun awaitRun() = await(TestRunState::hasRun)

	@Test
	fun `an engine landing in the background starts paused`() {
		val host = FakeHost()
		host.foreground = false
		TestRunState.start(host)
		TestRunState.pause()
		awaitRun()
		val held = TestRunState.secondsLeft

		Thread.sleep(1_100)
		shadowOf(Looper.getMainLooper()).idle()

		assertEquals("a background landing never ticks", held, TestRunState.secondsLeft)
	}

	@Test
	fun `grading and result statistics need no compatibility repository`() {
		val host = FakeHost()
		TestRunState.start(host)
		awaitRun()
		TestRunState.setAnswer(0, "house")

		TestRunState.submit(host)
		await { host.results != null }

		val result = host.results!!.single()
		assertTrue(result.correct)
		assertEquals(1, result.word.passedTests)
	}

	@Test
	fun `a queued tick cannot update a cleared run`() {
		val host = FakeHost()
		TestRunState.start(host)
		awaitRun()
		// the first engine tick arrives before any sleep, so park it behind reset
		Thread.sleep(250)

		TestRunState.reset()
		shadowOf(Looper.getMainLooper()).idle()

		assertEquals(0, TestRunState.secondsLeft)
	}

	@Test
	fun `no live run offers nothing to abort`() {
		TestRunState.attach(FakeHost())

		TestRunState.abort("Sure?")

		assertFalse(Dialogs.isShowing<ContinuePopup>())
	}
}
