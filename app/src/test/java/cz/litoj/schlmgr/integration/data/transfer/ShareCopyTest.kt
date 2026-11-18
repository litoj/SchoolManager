package cz.litoj.schlmgr.integration.data.transfer

import android.net.Uri
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.transfer.FileGateway
import java.io.File
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
 * - the shared copy is the only folder another app ever sees.
 * - it holds one file, the one the last share handed over.
 * - the tests watch the file behind a false door, see [ShareProviderTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareCopyTest {

	private val ctx get() = RuntimeEnvironment.getApplication()
	private val shared get() = File(ctx.cacheDir, "shared")

	/** The real door caches one folder per JVM, so the suite keeps it for one test only. */
	private val realDoor = FileGateway.shareUri

	@Before
	fun setUp() {
		GlobalDependencies.appContext = ctx
		FileGateway.shareUri = { _, file -> Uri.fromFile(file) }
	}

	@After
	fun tearDown() {
		FileGateway.shareUri = realDoor
	}

	private fun copiedText(): String {
		val files = shared.listFiles()!!
		assertEquals("the folder holds the one shared copy", 1, files.size)
		return files.single().readText()
	}

	@Test
	fun `the door receives the copy inside the shared folder`() {
		val uri = FileGateway.shareCopy(ctx, "Kapitel.txt", "w1;t1\n")

		assertEquals(Uri.fromFile(File(shared, "Kapitel.txt")), uri)
	}

	@Test
	fun `the copy carries the exported text`() {
		FileGateway.shareCopy(ctx, "Kapitel.txt", "w1;t1\n")

		assertEquals("w1;t1\n", copiedText())
	}

	@Test
	fun `an older copy leaves before the new one arrives`() {
		FileGateway.shareCopy(ctx, "Old.txt", "old\n")

		FileGateway.shareCopy(ctx, "New.txt", "new\n")

		assertEquals("new\n", copiedText())
	}

	@Test
	fun `a name of path characters lands as one file of its own`() {
		FileGateway.shareCopy(ctx, "a/b\\c:d.txt", "text\n")

		assertEquals("text\n", copiedText())
		assertTrue("the name keeps no path character", '_' in shared.list()!!.single())
	}

	@Test
	fun `an endless name is cut to a file name`() {
		FileGateway.shareCopy(ctx, "n".repeat(400) + ".txt", "text\n")

		assertTrue("the name stays short", shared.list()!!.single().length < 100)
	}
}
