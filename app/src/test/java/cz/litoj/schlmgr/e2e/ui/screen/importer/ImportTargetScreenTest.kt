package cz.litoj.schlmgr.e2e.ui.screen.importer

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.ui.screen.importer.ImportTargetScreen
import cz.litoj.schlmgr.ui.screen.importer.ImportTargetViewModel
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

/**
 * - the mover keeps its bar and opens a target.
 * - an import lands the file, turns the bar into Done, reveals the list
 *   end and opens a single top-level container.
 * - Done hands the open path over to the caller.
 * - a busy import locks the target rows and the cancel.
 * - a failed import keeps the bar, nothing stays busy or imported.
 * - the picked documents are shadow-registered content uris.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportTargetScreenTest {

	@get:Rule
	val rule = createAndroidComposeRule<ComponentActivity>()

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

	/** The import flows drive clicks, so an open permission dialog must not eat them. */
	private fun grantProgressPermission() {
		shadowOf(RuntimeEnvironment.getApplication())
			.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	/** The mover over a named document, with the rows of the seeded subject. */
	private fun mover(
		text: String = "",
		name: String = "list.txt",
		onDone: (List<Int>) -> Unit = {},
	): ImportTargetViewModel {
		val subject = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(subject.id, "B", null)
		val model = ImportTargetViewModel(picked(text), Dispatchers.Unconfined) { name }
		rule.setContent { ImportTargetScreen(model, {}, onDone) }
		rule.waitForIdle()
		return model
	}

	/** The shadow serves the bytes of the text for every read of the uri. */
	private fun picked(text: String): Uri {
		val uri = Uri.parse("content://schlmgr.test/pick/${System.identityHashCode(text)}")
		val bytes = text.toByteArray(StandardCharsets.UTF_8)
		shadowOf(RuntimeEnvironment.getApplication().contentResolver)
			.registerInputStream(uri, object : java.io.ByteArrayInputStream(bytes) {
				override fun close() { reset() }
			})
		return uri
	}

	/** The shadow serves a stream that reads only once the gate opens. */
	private fun blocked(gate: CountDownLatch): Uri {
		val uri = Uri.parse("content://schlmgr.test/pick/blocked")
		val bytes = "Kapitel {\nHaus;home\n}".toByteArray(StandardCharsets.UTF_8)
		shadowOf(RuntimeEnvironment.getApplication().contentResolver)
			.registerInputStream(uri, object : java.io.ByteArrayInputStream(bytes) {
				override fun read(): Int {
					gate.await()
					return super.read()
				}

				override fun read(b: ByteArray, off: Int, len: Int): Int {
					gate.await()
					return super.read(b, off, len)
				}

				override fun close() { reset() }
			})
		return uri
	}

	private fun label(res: Int, vararg args: Any): String =
		RuntimeEnvironment.getApplication().getString(res, *args)

	@Test
	fun `the mover keeps its bar and opens a target`() {
		grantProgressPermission()
		mover()

		rule.onNodeWithText(label(R.string.objects_cancel)).assertExists()
		rule.onNodeWithText(label(R.string.import_here)).assertExists()
		rule.onNodeWithText(label(R.string.import_file_title, "list.txt")).assertExists()

		rule.onNodeWithText("A").performClick()
		rule.waitForIdle()

		rule.onNodeWithText("B").assertExists()
		rule.onNodeWithText(label(R.string.objects_cancel)).assertExists()
		rule.onNodeWithText(label(R.string.import_here)).assertExists()
	}

	@Test
	fun `an ungranted mover asks for the progress permission`() {
		val model = ImportTargetViewModel(picked(""), Dispatchers.Unconfined) { "list.txt" }
		rule.setContent { ImportTargetScreen(model, onExit = {}, onDone = {}) }
		rule.waitForIdle()

		// the bar belongs to the mover, no subjects screen ever asked before it
		val request = rule.runOnIdle { shadowOf(rule.activity).lastRequestedPermission }
		assertNotNull("the mover never asked for the progress permission", request)
		assertTrue(android.Manifest.permission.POST_NOTIFICATIONS
			in request!!.requestedPermissions)
	}

	@Test
	fun `an import lands, turns the bar into Done and opens the single top-level container`() {
		grantProgressPermission()
		val words = (1..10).joinToString("") { "w$it;t$it\n" }
		var donePath: List<Int>? = null
		val model = mover("Kapitel {\n$words}") { donePath = it }

		rule.onNodeWithText(label(R.string.import_here)).performClick()
		rule.waitForIdle()

		rule.runOnIdle { assertTrue(model.targetState.value.imported) }
		rule.runOnIdle {
			// a single top-level chapter opened, the mover shows its words
			assertEquals((1..10).map { "w$it" }, model.items().map { it.item.name })
			assertTrue("the reload asks for the list end",
				model.listState.value.scrollToBottom > 0)
		}
		// the revealed end shows the last row. A word shows its translation
		// by default, so the last row reads t10
		rule.onNodeWithText("t10").assertIsDisplayed()
		// the bar offers no second import
		rule.onNodeWithText(label(R.string.import_here)).assertDoesNotExist()
		rule.onNodeWithText(label(R.string.done)).performClick()
		rule.waitForIdle()

		// Done hands the opened path over to the caller
		val kapitel = ItemRepository.subjects().single { it.name == "Kapitel" }
		assertEquals(listOf(kapitel.id), donePath)
	}

	@Test
	fun `a busy import locks the target rows and the cancel`() {
		grantProgressPermission()
		ItemRepository.createSubject("A", null)
		val gate = CountDownLatch(1)
		val model = ImportTargetViewModel(blocked(gate), Dispatchers.IO) { "list.txt" }
		var exited = false
		rule.setContent { ImportTargetScreen(model, onExit = { exited = true }, onDone = {}) }
		rule.waitForIdle()

		rule.onNodeWithText(label(R.string.import_here)).performClick()
		rule.runOnIdle { assertTrue("the import runs", model.targetState.value.busy) }

		// the locked mover refuses the navigation into a row and the exit
		rule.onNodeWithText("A").performClick()
		rule.onNodeWithText(label(R.string.objects_cancel)).performClick()
		rule.runOnIdle {
			assertEquals("the locked mover keeps the root",
				listOf("A"), model.items().map { it.item.name })
			assertFalse("the locked mover keeps the window", exited)
		}

		// the release lands the file and clears the lock
		gate.countDown()
		drainUntil { model.targetState.value.imported }
		rule.runOnIdle { assertFalse(model.targetState.value.busy) }
	}

	/** Polls the condition between idles, since each idle drains the main queue. */
	private fun drainUntil(timeout: Long = 5_000, condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + timeout
		while (!condition()) {
			if (System.currentTimeMillis() > deadline)
				throw AssertionError("the condition never held")
			rule.runOnIdle { }
			Thread.sleep(10)
		}
	}

	@Test
	fun `a failed import keeps the bar, nothing stays busy or imported`() {
		grantProgressPermission()
		val model = mover("Haus\n", "Broken.txt")

		rule.onNodeWithText(label(R.string.import_here)).performClick()
		rule.waitForIdle()

		rule.runOnIdle {
			assertFalse(model.targetState.value.imported)
			assertFalse(model.targetState.value.busy)
		}
		// the bar still offers the import
		rule.onNodeWithText(label(R.string.import_here)).assertExists()
	}
}
